(ns ctia.entity.event-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.schemas.common :refer [ctim-schema-version]]
   [ctim.domain.id :refer [str->short-id]]
   [clojure
    [string :as str]
    [test :refer [is testing]]]
   [clj-momo.lib.time :as time]
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest join-fixtures use-fixtures]]
   [ctim.domain.id :as id]
   [ctia.test-helpers
    [auth :refer [all-capabilities]]
    [core :as helpers
     :refer [delete post put get fixture-with-fixed-time
             with-sequential-uuid]]
    [fake-whoami-service :as whoami-helpers]
    [store :refer [test-for-each-store]]]
   [ctim.examples.incidents :refer [new-incident-minimal]]
   [ctim.examples.casebooks :refer [new-casebook-minimal]]
   [ctim.domain.id :as id]
   [cemerick.url :refer [url-encode]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-event-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "user1"
                                ["group1"]
                                "user1"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user1"
                                         "user1"
                                         "group1"
                                         "user1")

     (helpers/set-capabilities! "user2"
                                ["group1"]
                                "user2"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user2"
                                         "user2"
                                         "group1"
                                         "user2")

     (helpers/set-capabilities! "user3"
                                ["group2"]
                                "user3"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user3"
                                         "user3"
                                         "group2"
                                         "user3")

     (testing "simulate Incident activity"
       (with-sequential-uuid
         (fn []
           (fixture-with-fixed-time
            (time/timestamp "2042-01-01")
            (fn []
              (let [{incident :parsed-body
                     incident-status :status}
                    (post (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description"
                                       :incident_time
                                       {:opened (time/timestamp "2042-01-01")})
                          :headers {"Authorization" "user1"})

                    {incident-user-3 :parsed-body
                     incident-user-3-status :status}
                    (post (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description")
                          :headers {"Authorization" "user3"})
                    {updated-incident :parsed-body
                     updated-incident-status :status}
                    (fixture-with-fixed-time
                     (time/timestamp "2042-01-02")
                     (fn []
                       (put (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :body (assoc incident
                                         :description "changed description")
                            :headers {"Authorization" "user2"})))

                    {casebook :parsed-body
                     casebook-status :status}
                    (post (str "ctia/casebook")
                          :body (assoc new-casebook-minimal
                                       :tlp "amber")
                          :headers {"Authorization" "user1"})

                    {incident-casebook-link :parsed-body
                     incident-casebook-link-status :status}
                    (post (format "ctia/%s/%s/link"
                                  "incident"
                                  (-> (:id incident)
                                      id/long-id->id
                                      :short-id))
                          :body {:casebook_id (:id casebook)}
                          :headers {"Authorization" "user1"})
                    {incident-delete-body :parsed-body
                     incident-delete-status :status}
                    (delete (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :headers {"Authorization" "user1"})]

                (is (= 201 incident-status))
                (is (= 201 incident-user-3-status))
                (is (= 200 updated-incident-status))
                (is (= 201 casebook-status))
                (is (= 201 incident-casebook-link-status))
                (is (= 204 incident-delete-status))

                (testing "should be able to list all related incident events filtered with Access control"
                  (let [q (url-encode
                           (format "entity.id:\"%s\" OR entity.source_ref:\"%s\" OR entity.target_ref:\"%s\""
                                   (:id incident)
                                   (:id incident)
                                   (:id incident)))
                        results (:parsed-body (get (str "ctia/event/search?query=" q)
                                                   :content-type :json
                                                   :headers {"Authorization" "user1"}))
                        event-id (-> results
                                     first
                                     :id
                                     id/long-id->id
                                     :short-id)]
                    (testing "should be able to GET an event"
                      (is (= 200 (:status (get (str "ctia/event/" event-id)
                                               :headers {"Authorization" "user1"})))))

                    (is (= [{:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "my description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-01T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111113",
                             :type "event",
                             :event_type :record-created}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-02T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "changed description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-02T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111116",
                             :type "event",
                             :event_type :record-updated,
                             :fields
                             [{:field :modified,
                               :action "modified",
                               :change
                               {:before "2042-01-01T00:00:00.000Z",
                                :after "2042-01-02T00:00:00.000Z"}}
                              {:field :description,
                               :action "modified",
                               :change
                               {:before "my description",
                                :after "changed description"}}]}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:schema_version ctim-schema-version,
                              :target_ref
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :type "relationship",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-01T00:00:00.000Z",
                              :source_ref
                              "http://localhost:3001/ctia/casebook/casebook-00000000-0000-0000-0000-111111111117",
                              :id
                              "http://localhost:3001/ctia/relationship/relationship-00000000-0000-0000-0000-111111111119",
                              :tlp "amber",
                              :groups ["group1"],
                              :owner "user1",
                              :relationship_type "related-to"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111120",
                             :type "event",
                             :event_type :record-created}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "changed description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-02T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111121",
                             :type "event",
                             :event_type :record-deleted}]
                           results)))))))))))))
