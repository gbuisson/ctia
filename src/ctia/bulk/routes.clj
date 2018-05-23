(ns ctia.bulk.routes
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.bulk
    [core :refer [bulk-size create-bulk fetch-bulk get-bulk-max-size]]
    [schemas :refer [Bulk BulkRefs NewBulk]]]
   [ctia.http.routes.common :as common]
   [ctia.schemas.core :refer [Reference]]
   [ring.util.http-response :refer :all]
   [schema.core :as s]))

(defroutes bulk-routes
  (POST "/" []
        :return BulkRefs
        :body [bulk NewBulk {:description "a new Bulk object"}]
        :header-params [{Authorization :- (s/maybe s/Str) nil}]
        :summary "POST many new entities using a single HTTP call"
        :auth-identity login
        :capabilities #{:create-actor
                        :create-attack-pattern
                        :create-campaign
                        :create-coa
                        :create-data-table
                        :create-exploit-target
                        :create-feedback
                        :create-incident
                        :create-investigation
                        :create-indicator
                        :create-judgement
                        :create-malware
                        :create-relationship
                        :create-casebook
                        :create-sighting
                        :create-tool}
        (if (> (bulk-size bulk)
               (get-bulk-max-size))
          (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
          (common/created (create-bulk bulk login))))

  (GET "/" []
       :return (s/maybe Bulk)
       :summary "GET many entities at once"
       :query-params [{actors          :- [Reference] []}
                      {attack_patterns :- [Reference] []}
                      {campaigns       :- [Reference] []}
                      {coas            :- [Reference] []}
                      {data_tables     :- [Reference] []}
                      {exploit_targets :- [Reference] []}
                      {feedbacks       :- [Reference] []}
                      {incidents       :- [Reference] []}
                      {indicators      :- [Reference] []}
                      {investigations  :- [Reference] []}
                      {judgements      :- [Reference] []}
                      {malwares        :- [Reference] []}
                      {relationships   :- [Reference] []}
                      {casebooks       :- [Reference] []}
                      {sightings       :- [Reference] []}
                      {tools           :- [Reference] []}]
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities #{:read-actor
                       :read-attack-pattern
                       :read-campaign
                       :read-coa
                       :read-data-table
                       :read-exploit-target
                       :read-feedback
                       :read-incident
                       :read-indicator
                       :read-investigation
                       :read-judgement
                       :read-malware
                       :read-relationship
                       :read-casebook
                       :read-sighting
                       :read-tool}
       :auth-identity auth-identity
       (let [entities-map {:actors          actors
                           :attack_patterns attack_patterns
                           :campaigns       campaigns
                           :coas            coas
                           :data_tables     data_tables
                           :exploit_targets exploit_targets
                           :feedbacks       feedbacks
                           :incidents       incidents
                           :investigations  investigations
                           :indicators      indicators
                           :judgements      judgements
                           :malwares        malwares
                           :relationships   relationships
                           :casebooks       casebooks
                           :sightings       sightings
                           :tools           tools}]
         (ok (fetch-bulk entities-map auth-identity)))))
