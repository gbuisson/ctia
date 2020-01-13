(ns ctia.entity.feed
  (:require
   [ctia.lib.csv
    :refer [csv]]
   [ctia.store :refer [list-all-pages
                       list-records
                       read-record
                       read-store]]
   [compojure.api.middleware :refer [api-middleware-defaults ->mime-types]]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response
    :refer [ok unauthorized not-found]]
   [compojure.api.sweet :refer [GET routes]]
   [ctia.entity.judgement.schemas :refer [Judgement]]
   [ctia.schemas.core :refer [Observable]]
   [ctia.domain.entities :refer [un-store
                                 with-long-id
                                 long-id->id]]
   [ctia.entity.feed.schemas
    :refer
    [NewFeed
     Feed
     PartialFeed
     PartialFeedList
     PartialStoredFeed
     StoredFeed
     realize-feed]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams
                    PagingParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.stores.es
    [mapping :as em]
    [store :refer [def-es-store]]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [ctia.schemas.sorting :as sorting]))

(def feed-mapping
  {"feed"
   {:dynamic false
    :properties
    (merge em/base-entity-mapping
           em/stored-entity-mapping
           {:title em/all_text
            :lifetime em/valid-time
            :output em/token
            :secret em/token
            :feed_view_url em/token
            :feed_view_url_csv em/token
            :indicator_id em/token})}})

(def-es-store FeedStore :feed StoredFeed PartialStoredFeed)
(def feed-fields
  (concat
   sorting/base-entity-sort-fields
   [:title
    :output
    :indicator_id]))

(def sort-restricted-feed-fields
  (remove #{:title} feed-fields))

(def feed-sort-fields
  (apply s/enum sort-restricted-feed-fields))

(s/defschema FeedFieldsParam
  {(s/optional-key :fields) [feed-sort-fields]})

(s/defschema FeedSearchParams
  (st/merge
   {:query s/Str}
   PagingParams
   BaseEntityFilterParams
   FeedFieldsParam
   {(s/optional-key :sort_by) feed-sort-fields}))

(def FeedGetParams FeedFieldsParam)

(s/defschema FeedListQueryParams
  (st/merge
   PagingParams
   FeedFieldsParam
   {(s/optional-key :sort_by) feed-sort-fields}))

(s/defschema FeedByExternalIdQueryParams
  FeedListQueryParams)

(s/defschema FeedView
  {(s/optional-key :judgements) [Judgement]
   (s/optional-key :observables) [Observable]})

(def identity-map
  {:authorized-anonymous true})

(defn fetch-feed [id s]
  (if-let [{:keys [indicator_id
                   secret
                   output]}
           (read-store :feed
                       read-record
                       id
                       identity-map
                       {})]

    (if (= s secret)
      (let [feed-results
            (some->> (list-all-pages
                      :relationship
                      list-records
                      {:all-of {:target_ref indicator_id}}
                      identity-map
                      {:fields [:source_ref]})
                     (map :source_ref)
                     (remove nil?)
                     (map #(read-store :judgement
                                       read-record
                                       %
                                       identity-map
                                       {}))
                     (remove nil?))]
        (if (seq feed-results)
          (into {:output output}
                (if (= :observables output)
                  {:observables (distinct (map :observable feed-results))}
                  {:judgements (distinct feed-results)}))
          {}))
      :unauthorized)
    :not-found))

(defn sorted-observable-values [data]
  (sort-by :value
           (map #(select-keys % [:value]) data)))

(def feed-view-routes
  (routes
   (GET "/:id/view.csv" []
        :summary "Get a Feed View as a CSV"
        :path-params [id :- s/Str]
        :return s/Str
        :produces #{"text/csv"}
        :query-params [s :- (describe s/Str "The feed share token")]
        (let [{:keys [output]
               :as feed} (fetch-feed id s)]
          (case feed
            :not-found (not-found "feed not found")
            :unauthorized (unauthorized "wrong secret")
            (let [data (output feed)
                  res-str
                  (if (= (:observables output))
                    (sorted-observable-values data)
                    data)]
              (csv res-str (str id ".csv") false)))))
   (GET "/:id/view" []
        :summary "Get a Feed View"
        :path-params [id :- s/Str]
        :return FeedView
        :query-params [s :- (describe s/Str "The feed share token")]
        (let [feed (fetch-feed id s)]
          (case feed
            :not-found (not-found "feed not found")
            :unauthorized (unauthorized "wrong secret")
            (ok (dissoc feed :output)))))))

(def feed-routes
  (routes
   feed-view-routes
   (entity-crud-routes
    {:api-tags ["Feed"]
     :entity :feed
     :new-schema NewFeed
     :entity-schema Feed
     :get-schema PartialFeed
     :get-params FeedGetParams
     :list-schema PartialFeedList
     :search-schema PartialFeedList
     :external-id-q-params FeedByExternalIdQueryParams
     :search-q-params FeedSearchParams
     :new-spec :new-feed/map
     :realize-fn realize-feed
     :get-capabilities :read-feed
     :post-capabilities :create-feed
     :put-capabilities :create-feed
     :delete-capabilities :delete-feed
     :search-capabilities :search-feed
     :external-id-capabilities :read-feed
     :hide-delete? false})))

(def capabilities
  #{:create-feed
    :read-feed
    :delete-feed
    :search-feed})

(def feed-entity
  {:route-context "/feed"
   :tags ["Feed"]
   :entity :feed
   :plural :feeds
   :new-spec :new-feed/map
   :schema Feed
   :partial-schema PartialFeed
   :partial-list-schema PartialFeedList
   :new-schema NewFeed
   :stored-schema StoredFeed
   :partial-stored-schema PartialStoredFeed
   :realize-fn realize-feed
   :es-store ->FeedStore
   :es-mapping feed-mapping
   :routes feed-routes
   :capabilities capabilities})
