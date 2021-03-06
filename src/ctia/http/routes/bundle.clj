(ns ctia.http.routes.bundle
  (:require [compojure.api.sweet :refer :all]
            [ctia.auth :as auth]
            [ctia.http.routes.bulk :as bulk]
            [ctia.lib.keyword :refer [singular]]
            [ctia.properties :refer [properties]]
            [ctia.schemas.bulk :refer [Bulk]]
            [ctia.schemas.core :refer [NewBundle TempIDs]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ctim.domain.id :as id]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [clojure.string :as string]))

(s/defschema EntityImportResult
  (st/optional-keys
   {:id s/Str
    :original_id s/Str
    :result (s/enum "error" "created" "exists")
    :type s/Keyword
    :external_id s/Str
    :error s/Any}))

(s/defschema EntityImportData
  "Data structure used to keep a link between
   the transient ID, the external_id and the ID of an entity
   during the import process."
  (st/merge
   EntityImportResult
   (st/optional-keys
    {:new-entity s/Any
     :old-entity s/Any})))

(s/defschema BundleImportData {s/Keyword [EntityImportData]})

(s/defschema BundleImportResult
  {:results [EntityImportResult]})

(defn list-fn
  "Returns the list function for a given entity type"
  [entity-type]
  (case entity-type
    :actor          list-actors
    :attack-pattern list-attack-patterns
    :campaign       list-campaigns
    :coa            list-coas
    :data-table     list-data-tables
    :exploit-target list-exploit-targets
    :feedback       list-feedback
    :incident       list-incidents
    :indicator      list-indicators
    :judgement      list-judgements
    :malware        list-malwares
    :relationship   list-relationships
    :sighting       list-sightings
    :tool           list-tools))

(defn transient-id?
  [id]
  (and id (re-matches id/transient-id-re id)))

(def default-external-key-prefixes "ctia-")

(defn debug [msg v]
  (log/debug msg v)
  v)

(s/defn prefixed-external-ids :- [s/Str]
  "Returns all external IDs prefixed by the given key-prefix."
  [key-prefix external-ids]
  (->> external-ids
       (filter #(str/starts-with? % key-prefix))))

(s/defn valid-external-id :- (s/maybe s/Str)
  "Returns the external ID that can be used to check whether an entity has
  already been imported or not."
  [external-ids key-prefixes]
  (let [valid-external-ids (mapcat #(prefixed-external-ids % external-ids)
                                   key-prefixes)]
    (if (> (count valid-external-ids) 1)
      (log/warnf (str "More than 1 valid external ID has been found "
                      "(key-prefixes:%s | external-ids:%s)")
                 (pr-str key-prefixes) (pr-str external-ids))
      (first valid-external-ids))))

(s/defn parse-key-prefixes :- [s/Str]
  "Parses a comma separated list of external ID prefixes"
  [s :- (s/maybe s/Str)]
  (when s
    (map string/trim
         (string/split s #","))))

(s/defn entity->import-data :- EntityImportData
  "Creates import data related to an entity"
  [{:keys [id external_ids] :as entity}
   entity-type
   external-key-prefixes]
  (let [key-prefixes (parse-key-prefixes
                      (or external-key-prefixes
                          (get-in @properties
                                  [:ctia :store :external-key-prefixes]
                                  default-external-key-prefixes)))
        external_id (valid-external-id external_ids key-prefixes)]
    (when (not external_id)
      (log/warnf "No valid external ID has been provided (id:%s)" id))
    (cond-> {:new-entity entity
             :type entity-type}
      (transient-id? id) (assoc :original_id id)
      external_id (assoc :external_id external_id))))

(def find-by-external-ids-limit 1000)

(defn all-pages
  "Retrieves all external ids using pagination."
  [f]
  (loop [paging {:offset 0
                 :limit find-by-external-ids-limit}
         entities []]
    (let [{results :data
           {next-page :next} :paging} (f paging)
          acc-entities (into entities results)]
      (if next-page
        (recur next-page acc-entities)
        acc-entities))))

(defn find-by-external-ids
  [import-data entity-type auth-identity]
  (let [external-ids (remove nil? (map :external_id import-data))]
    (log/debugf "Searching %s matching these external_ids %s"
                entity-type
                (pr-str external-ids))
    (if (seq external-ids)
      (debug (format "Results for %s:" (pr-str external-ids))
             (all-pages
              (fn [paging]
                (read-store entity-type (list-fn entity-type)
                            {:external_ids external-ids}
                            (auth/ident->map auth-identity)
                            paging))))
      [])))

(defn by-external-id
  "Index entities by external_id

   Ex:
   {{:external_id \"ctia-1\"} {:external_id \"ctia-1\"
                               :entity {...}}
    {:external_id \"ctia-2\"} {:external_id \"ctia-2\"
                               :entity {...}}}"
  [entities]
  (let [entity-with-external-id
        (->> entities
             (map (fn [{:keys [external_ids] :as entity}]
                    (set (map (fn [external_id]
                                {:external_id external_id
                                 :entity entity})
                              external_ids))))
             (apply set/union))]
    (set/index entity-with-external-id [:external_id])))

(s/defn entities-import-data->tempids :- TempIDs
  "Get a mapping table between orignal IDs and real IDs"
  [import-data :- [EntityImportData]]
  (->> import-data
       (filter #(and (:original_id %)
                     (:id %)))
       (map (fn [{:keys [original_id id]}]
              [original_id id]))
       (into {})))

(def entities-keys (map :k (keys Bulk)))

(defn map-kv
  "Apply a function to all entity collections within a map
  of entities indexed by entity type."
  [f m]
  (into {}
        (map (fn [[k v]]
               [k (f k v)])
             m)))

(s/defn init-import-data :- [EntityImportData]
  "Create import data for a type of entities"
  [entities
   entity-type
   external-key-prefixes]
  (map #(entity->import-data % entity-type external-key-prefixes)
       entities))

(s/defn with-existing-entity :- EntityImportData
  "If the entity has already been imported, update the import data
   with its ID. If more than one old entity is linked to an external id,
   an error is reported."
  [{:keys [external_id]
    entity-type :type
    :as entity-data} :- EntityImportData
   entity-type
   find-by-external-id]
  (if-let [old-entities (find-by-external-id external_id)]
    (let [with-long-id-fn (bulk/with-long-id-fn entity-type)
          old-entity (some-> old-entities
                             first
                             :entity
                             with-long-id-fn
                             ent/un-store)
          num-old-entities (count old-entities)]
      (cond-> entity-data
        ;; only one entity linked to the external ID
        (and old-entity
             (= num-old-entities 1)) (assoc :result "exists"
                                            ;;:old-entity old-entity
                                            :id (:id old-entity))
        ;; more than one entity linked to the external ID
        (> num-old-entities 1)
        (assoc :result "error"
               :error
               (format
                (str "More than one entity is "
                     "linked to the external id %s (%s)")
                external_id
                (pr-str (map :id old-entities))))))
    entity-data))

(s/defn with-existing-entities :- [EntityImportData]
  "Add existing entities to the import data map."
  [import-data entity-type identity-map]
  (let [entities-by-external-id
        (-> (find-by-external-ids import-data
                                  entity-type
                                  identity-map)
            by-external-id)
        find-by-external-id-fn (fn [external_id]
                                 (when external_id
                                   (get entities-by-external-id
                                        {:external_id external_id})))]
    (map #(with-existing-entity % entity-type find-by-external-id-fn)
         import-data)))

(s/defn prepare-import :- BundleImportData
  "Prepares the import data by searching all existing
   entities based on their external IDs. Only new entities
   will be imported"
  [bundle-entities
   external-key-prefixes
   auth-identity]
  (map-kv (fn [k v]
            (let [entity-type (singular k)]
              (-> v
                  (init-import-data entity-type external-key-prefixes)
                  (with-existing-entities entity-type auth-identity))))
          bundle-entities))

(defn create?
  "Whether the provided entity should be created or not"
  [{:keys [result] :as entity}]
  ;; Add only new entities without error
  (not (contains? #{"error" "exists"} result)))

(s/defn prepare-bulk
  "Creates the bulk data structure with all entities to create."
  [bundle-import-data :- BundleImportData]
  (map-kv (fn [k v]
            (->> v
                 (filter create?)
                 (remove nil?)
                 (map :new-entity)))
          bundle-import-data))

(s/defn with-bulk-result
  "Set the bulk result to the bundle import data"
  [bundle-import-data :- BundleImportData
   {:keys [tempids] :as bulk-result}]
  (map-kv (fn [k v]
            (let [{submitted true
                   not-submitted false} (group-by create? v)]
              (concat
               ;; Only submitted entities are processed
               (map (fn [entity-import-data
                         {:keys [error] :as entity-bulk-result}]
                      (cond-> entity-import-data
                        error (assoc :error error
                                     :result "error")
                        (not error) (assoc :id entity-bulk-result
                                           :result "created")))
                    submitted (get bulk-result k))
               not-submitted)))
          bundle-import-data))

(s/defn build-response :- BundleImportResult
  "Build bundle import response"
  [bundle-import-data :- BundleImportData]
  {:results (map
             #(dissoc % :new-entity :old-entity)
             (apply concat (vals bundle-import-data)))})

(defn bulk-params []
  {:refresh
   (get-in @properties [:ctia :store :bundle-refresh] false)})

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   external-key-prefixes :- (s/maybe s/Str)
   auth-identity :- (s/protocol auth/IIdentity)]
  (let [bundle-entities (select-keys bundle entities-keys)
        bundle-import-data (prepare-import bundle-entities
                                           external-key-prefixes
                                           auth-identity)
        bulk (debug "Bulk" (prepare-bulk bundle-import-data))
        tempids (->> bundle-import-data
                     (map (fn [[_ entities-import-data]]
                            (entities-import-data->tempids entities-import-data)))
                     (apply merge {}))]
    (debug "Import bundle response"
           (->> (bulk/create-bulk bulk tempids auth-identity (bulk-params))
                (with-bulk-result bundle-import-data)
                build-response))))

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
                 :query-params
                 [{external-key-prefixes
                   :- (describe s/Str "Comma separated list of external key prefixes")
                   nil}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "POST many new entities using a single HTTP call"
                 :identity auth-identity
                 :capabilities #{:create-actor
                                 :create-attack-pattern
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-exploit-target
                                 :create-feedback
                                 :create-incident
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
                                 :create-sighting
                                 :create-tool
                                 :import-bundle}
                 (if (> (bulk/bulk-size bundle)
                        (bulk/get-bulk-max-size))
                   (bad-request (str "Bundle max nb of entities: " (bulk/get-bulk-max-size)))
                   (ok (import-bundle bundle external-key-prefixes auth-identity))))))
