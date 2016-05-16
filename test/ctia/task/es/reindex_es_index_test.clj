(ns ctia.task.es.reindex-es-index-test
  (:require [clojure.test :refer [deftest join-fixtures testing use-fixtures]]
            [ctia.stores.es.store :refer [read-store-index-spec]]
            [ctia.task.es.reindex-es-index :as sut]
            [ctia.test-helpers
             [es :as es-helpers]
             [core :as helpers]
             [http :refer [assert-post]]]))

(use-fixtures :each (join-fixtures [es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia
                                    es-helpers/fixture-recreate-store-indexes]))

(deftest test-es-reingestion-task

  (testing "with test setup"
    (let [judgement-fixtures (repeat 100 {:observable {:value "1.2.3.4"
                                                       :type "ip"}
                                          :disposition 2
                                          :source "test"
                                          :priority 100
                                          :severity 100
                                          :confidence "Low"
                                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                                          :indicators [{:confidence "High"
                                                        :source "source"
                                                        :relationship "relationship"
                                                        :indicator_id "indicator-123"}]})
          actor-fixtures (repeat 100 {:title "actor"
                                      :description "description"
                                      :actor_type "Hacker"
                                      :source "a source"
                                      :confidence "High"
                                      :associated_actors [{:actor_id "actor-123"}
                                                          {:actor_id "actor-456"}]
                                      :associated_campaigns [{:campaign_id "campaign-444"}
                                                             {:campaign_id "campaign-555"}]
                                      :observed_TTPs [{:ttp_id "ttp-333"}
                                                      {:ttp_id "ttp-999"}]
                                      :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                   :end_time "2016-07-11T00:40:48.212-00:00"}})]
      (doseq [j judgement-fixtures]
        (assert-post "ctia/judgement" j))

      (doseq [a actor-fixtures]
        (assert-post "ctia/actor" a))

      (testing "reindex test"
        (let [store-spec (read-store-index-spec)]
          (sut/reindex-store (:uri store-spec)
                             (:indexname store-spec)
                             "ctia_reindex_test4"
                             100))))))
