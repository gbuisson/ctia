(ns ctia.http.routes.scratchpad-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.schemas.common
    :refer [ctim-schema-version]]
   [clojure.set :refer [subset?]]
   [ctim.examples.scratchpads
    :refer [new-scratchpad-minimal
            new-scratchpad-maximal]]
   [ctia.schemas.sorting
    :refer [scratchpad-sort-fields]]
   [clj-momo.test-helpers
    [core :as mth]
    [http :refer [encode]]]
   [clojure
    [string :as str]
    [test :refer [is join-fixtures testing use-fixtures]]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.properties :refer [get-http-show]]
   [ctia.test-helpers
    [http :refer [doc-id->rel-url]]
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [delete get post put patch]]
    [fake-whoami-service :as whoami-helpers]
    [pagination :refer [pagination-test]]
    [field-selection :refer [field-selection-tests]]
    [search :refer [test-query-string-search]]
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]))


(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-scratchpad-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/scratchpad"
    (let [new-scratchpad (-> new-scratchpad-maximal
                             (dissoc :id)
                             (assoc :description "description"))
          {status :status
           scratchpad :parsed-body}
          (post "ctia/scratchpad"
                :body new-scratchpad
                :headers {"Authorization" "45c1f5e3f05d0"})
          scratchpad-id
          (id/long-id->id (:id scratchpad))

          scratchpad-external-ids
          (:external_ids scratchpad)]
      (is (= 201 status))
      (is (deep=
           (assoc new-scratchpad :id (id/long-id scratchpad-id)) scratchpad))

      (testing "the scratchpad ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    scratchpad-id)      (:hostname    show-props)))
          (is (= (:protocol    scratchpad-id)      (:protocol    show-props)))
          (is (= (:port        scratchpad-id)      (:port        show-props)))
          (is (= (:path-prefix scratchpad-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/scratchpad/:id"
        (let [response (get (str "ctia/scratchpad/" (:short-id scratchpad-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-scratchpad :id (id/long-id scratchpad-id)) scratchpad))))

      (test-query-string-search :scratchpad "description" :description)

      (testing "GET /ctia/scratchpad/external_id/:external_id"
        (let [response (get (format "ctia/scratchpad/external_id/%s"
                                    (encode (rand-nth scratchpad-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              scratchpads (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc scratchpad :id (id/long-id scratchpad-id))]
               scratchpads))))

      (testing "PUT /ctia/scratchpad/:id"
        (let [with-updates (assoc scratchpad
                                  :title "modified scratchpad")
              response (put (str "ctia/scratchpad/" (:short-id scratchpad-id))
                            :body with-updates
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               with-updates
               updated-scratchpad))))

      (testing "PATCH /ctia/scratchpad/:id"
        (let [updates {:title "patched scratchpad"}
              response (patch (str "ctia/scratchpad/" (:short-id scratchpad-id))
                              :body updates
                              :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= "patched scratchpad"
                 (:title updated-scratchpad)))

          (patch (str "ctia/scratchpad/" (:short-id scratchpad-id))
                 :body {:title "scratchpad"}
                 :headers {"Authorization" "45c1f5e3f05d0"})))
      
      ;; -------- partial update operation tests ------------

      ;; observables
      (testing "POST /ctia/scratchpad/:id/observables :add"
        (let [new-observables [{:type "ip" :value "42.42.42.42"}]
              expected-entity (update scratchpad :observables concat new-observables)
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/observables")
                             :body {:operation :add
                                    :observables new-observables}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= expected-entity updated-scratchpad))))

      (testing "POST /ctia/scratchpad/:id/observables :remove"
        (let [deleted-observables [{:value "85:28:cb:6a:21:41" :type "mac_address"}
                                   {:value "42.42.42.42" :type "ip"}]
              expected-entity (update scratchpad :observables #(remove (set deleted-observables) %))
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/observables")
                             :body {:operation :remove
                                    :observables deleted-observables}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= expected-entity
                     updated-scratchpad))))

      (testing "POST /ctia/scratchpad/:id/observables :replace"
        (let [observables [{:value "42.42.42.42" :type "ip"}]
              expected-entity (assoc scratchpad :observables observables)
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/observables")
                             :body {:operation :replace
                                    :observables observables}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= expected-entity
                     updated-scratchpad))))

      (testing "POST /ctia/scratchpad/:id/observables :replace"
        (let [observables (:observables new-scratchpad-maximal)
              expected-entity (assoc scratchpad :observables observables)
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/observables")
                             :body {:operation :replace
                                    :observables observables}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= expected-entity
                     updated-scratchpad))))


      ;; texts
      (testing "POST /ctia/scratchpad/:id/texts :add"
        (let [new-texts [{:type "some" :text "text"}]
              expected-entity (update scratchpad :texts concat new-texts)
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/texts")
                             :body {:operation :add
                                    :texts new-texts}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]


          (is (= 200 (:status response)))
          (is (deep= expected-entity updated-scratchpad))))

      (testing "POST /ctia/scratchpad/:id/texts :remove"
        (let [deleted-texts [{:type "some" :text "text"}]
              expected-entity (update scratchpad :texts #(remove (set deleted-texts) %))
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/texts")
                             :body {:operation :remove
                                    :texts deleted-texts}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= expected-entity updated-scratchpad))))

      (testing "POST /ctia/scratchpad/:id/texts :replace"
        (let [texts [{:type "text" :text "text"}]
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/texts")
                             :body {:operation :replace
                                    :texts texts}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= texts (:texts updated-scratchpad)))))

      (testing "POST /ctia/scratchpad/:id/texts :replace"
        (let [texts (:texts new-scratchpad-maximal)
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/texts")
                             :body {:operation :replace
                                    :texts texts}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= texts (:texts updated-scratchpad)))))

      ;; bundle
      (testing "POST /ctia/scratchpad/:id/bundle :add"
        (let [new-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                                :type "malware"
                                                :schema_version ctim-schema-version
                                                :name "TEST"
                                                :labels ["malware"]}}}
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/bundle")
                             :body {:operation :add
                                    :bundle new-bundle-entities}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]

          (is (= 200 (:status response)))
          (is (not= (:malwares updated-scratchpad)
                    (:malwares new-bundle-entities)))
          (is (subset? (set (:malwares new-bundle-entities))
                       (set (-> updated-scratchpad :bundle :malwares))))))

      (testing "POST /ctia/scratchpad/:id/bundle :remove"
        (let [deleted-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                                    :type "malware"
                                                    :schema_version ctim-schema-version
                                                    :name "TEST"
                                                    :labels ["malware"]}}}
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/bundle")
                             :body {:operation :remove
                                    :bundle deleted-bundle-entities}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= (update scratchpad :bundle dissoc :malwares)
                     (update updated-scratchpad :bundle dissoc :malwares)))
          (is (not (subset? (set (:malwares deleted-bundle-entities))
                            (set (-> updated-scratchpad :bundle :malwares)))))))

      (testing "POST /ctia/scratchpad/:id/bundle :replace"
        (let [bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                            :type "malware"
                                            :schema_version ctim-schema-version
                                            :name "TEST"
                                            :labels ["malware"]}}}
              response (post (str "ctia/scratchpad/" (:short-id scratchpad-id) "/bundle")
                             :body {:operation :replace
                                    :bundle bundle-entities}
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-scratchpad (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep= (update scratchpad :bundle dissoc :malwares)
                     (update updated-scratchpad :bundle dissoc :malwares)))
          (is (= (:malwares bundle-entities)
                 (-> updated-scratchpad :bundle :malwares)))))


      (testing "DELETE /ctia/scratchpad/:id"
        (let [response (delete (str "ctia/scratchpad/" (:short-id scratchpad-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/scratchpad/" (:short-id scratchpad-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))


  (testing "POST invalid /ctia/scratchpad"
    (let [{status :status
           body :body}
          (post "ctia/scratchpad"
                ;; This field has an invalid length
                :body (assoc new-scratchpad-minimal
                             :title (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-scratchpad-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/scratchpad"
                            :body (-> new-scratchpad-maximal
                                      (dissoc :id)
                                      (assoc :source (str "dotimes " %)
                                             :title "foo"))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/scratchpad/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     scratchpad-sort-fields)

    (field-selection-tests
     ["ctia/scratchpad/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     scratchpad-sort-fields)))

(deftest-for-each-store test-scratchpad-routes-access-control
  (access-control-test "scratchpad"
                       new-scratchpad-minimal
                       true
                       true))
