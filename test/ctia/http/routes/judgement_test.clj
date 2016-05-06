(ns ctia.http.routes.judgement-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.pagination :refer [pagination-test]]
   [ctia.schemas.judgement :refer [NewJudgement StoredJudgement]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-judgement-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "POST /ctia/judgement"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
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
                                              :indicator_id "indicator-123"}]}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :priority 100
            :severity 100
            :confidence "Low"
            :source "test"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :indicators [{:confidence "High"
                          :source "source"
                          :relationship "relationship"
                          :indicator_id "indicator-123"}]
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))

      (testing "GET /ctia/judgement/:id"
        (let [response (get (str "ctia/judgement/" (:id judgement))
                            :headers {"api_key" "45c1f5e3f05d0"})
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :indicators [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :indicator_id "indicator-123"}]
                :owner "foouser"}
               (dissoc judgement
                       :id
                       :created)))))

      (testing "GET /ctia/judgement/:id with query-param api_key"
        (let [{status :status
               judgement :parsed-body
               :as response}
              (get (str "ctia/judgement/" (:id judgement))
                   :query-params {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :indicators [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :indicator_id "indicator-123"}]
                :owner "foouser"}
               (dissoc judgement
                       :id
                       :created)))))

      (testing "GET /ctia/judgement/:id authentication failures"
        (testing "no api_key"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement)))]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "unknown api_key"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement))
                     :headers {"api_key" "1111111111111"})]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "doesn't have read capability"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement))
                     :headers {"api_key" "2222222222222"})]
            (is (= 401 status))
            (is (= {:message "Missing capability",
                    :capabilities #{:admin :read-judgement},
                    :owner "baruser"}
                   body)))))

      (testing "DELETE /ctia/judgement/:id"
        (let [temp-judgement (-> (post "ctia/judgement"
                                       :body {:indicators [{:indicator_id "indicator-123"}]
                                              :observable {:value "9.8.7.6"
                                                           :type "ip"}
                                              :disposition 3
                                              :source "test"
                                              :priority 100
                                              :severity 100
                                              :confidence "Low"
                                              :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                                       :headers {"api_key" "45c1f5e3f05d0"})
                                 :parsed-body)
              response (delete (str "ctia/judgement/" (:id temp-judgement))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/judgement/" (:id temp-judgement))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))

      (testing "POST /ctia/judgement/:id/feedback"
        (let [response (post (str "ctia/judgement/" (:id judgement) "/feedback")
                             :body {:feedback -1
                                    :reason "false positive"}
                             :headers {"api_key" "45c1f5e3f05d0"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "feedback"
                :judgement (:id judgement),
                :feedback -1,
                :reason "false positive"
                :owner "foouser"}
               (dissoc feedback
                       :id
                       :created))))

        (testing "GET /ctia/judgement/:id/feedback"
          ;; create some more feedbacks
          (let [response (post "ctia/judgement"
                               :body {:indicators ["indicator-222"]
                                      :observable {:value "4.5.6.7"
                                                   :type "ip"}
                                      :disposition 1
                                      :source "test"}
                               :headers {"api_key" "45c1f5e3f05d0"})
                another-judgement (:parsed-body response)]
            (post (str "ctia/judgement/" (:id another-judgement) "/feedback")
                  :body {:feedback 0
                         :reason "yolo"}
                  :headers {"api_key" "45c1f5e3f05d0"}))
          (post (str "ctia/judgement/" (:id judgement) "/feedback")
                :body {:feedback 1
                       :reason "true positive"}
                :headers {"api_key" "45c1f5e3f05d0"})

          (let [response (get (str "ctia/judgement/" (:id judgement) "/feedback")
                              :headers {"api_key" "45c1f5e3f05d0"})
                feedbacks (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (deep=
                 #{{:type "feedback"
                    :judgement (:id judgement),
                    :feedback -1,
                    :reason "false positive"
                    :owner "foouser"}
                   {:type "feedback"
                    :judgement (:id judgement),
                    :feedback 1,
                    :reason "true positive"
                    :owner "foouser"}}
                 (set (map #(dissoc % :id :created)
                           feedbacks))))))))))

(deftest-for-each-store test-judgement-routes-for-dispositon-determination
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST a judgement with dispositon (id)"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with disposition_name"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition_name "Malicious"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement without disposition"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 5
            :disposition_name "Unknown"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with mismatching disposition/disposition_name"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 1
                                :disposition_name "Unknown"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 400 (:status response)))
      (is (deep=
           {:error "Mismatching :dispostion and dispositon_name for judgement",
            :judgement {:observable {:value "1.2.3.4"
                                     :type "ip"}
                        :disposition 1
                        :disposition_name "Unknown"
                        :source "test"
                        :priority 100
                        :severity 100
                        :confidence "Low"
                        :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
           (:parsed-body response))))))

(deftest-for-each-store test-judgement-routes-generative
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [new-judgements (g/sample 30 NewJudgement)
        ;;hardcode dispositon & observable
        fixed-judgements (map #(merge % {:observable {:type "ip"
                                                      :value "1.2.3.4"}
                                         :disposition 5
                                         :disposition_name "Unknown"}) new-judgements)]
    (testing "POST /ctia/judgement GET /ctia/judgement"
      (let [responses (map #(post "ctia/judgement"
                                  :body %
                                  :headers {"api_key" "45c1f5e3f05d0"}) fixed-judgements)]
        (doall (map #(is (= 200 (:status %))) responses))
        (is (deep=
             (set fixed-judgements)
             (->> responses
                  (map :parsed-body)
                  (map #(get (str "ctia/judgement/" (:id %))
                             :headers {"api_key" "45c1f5e3f05d0"}))
                  (map :parsed-body)
                  (map #(dissoc % :id :created :modified :owner))
                  set)))))

    (pagination-test
     "ctia/ip/1.2.3.4/judgements"
     {"api_key" "45c1f5e3f05d0"} [:id :disposition :priority :severity :confidence])))

