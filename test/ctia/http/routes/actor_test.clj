(ns ctia.http.routes.actor-test
  (:refer-clojure :exclude [get])
  (:require [ctim.examples.actors
             :refer [new-actor-minimal
                     new-actor-maximal]]
            [ctia.schemas.sorting
             :refer [actor-sort-fields]]
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
             [core :as helpers :refer [delete get post put]]
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

(deftest-for-each-store test-actor-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/actor"
    (let [new-actor (-> new-actor-maximal
                        (dissoc :id)
                        (assoc :description "description"))
          {status :status
           actor :parsed-body}
          (post "ctia/actor"
                :body new-actor
                :headers {"Authorization" "45c1f5e3f05d0"})

          actor-id
          (id/long-id->id (:id actor))

          actor-external-ids
          (:external_ids actor)]
      (is (= 201 status))
      (is (deep=
           (assoc new-actor :id (id/long-id actor-id)) actor))

      (testing "the actor ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    actor-id)      (:hostname    show-props)))
          (is (= (:protocol    actor-id)      (:protocol    show-props)))
          (is (= (:port        actor-id)      (:port        show-props)))
          (is (= (:path-prefix actor-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/actor/:id"
        (let [response (get (str "ctia/actor/" (:short-id actor-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-actor :id (id/long-id actor-id)) actor))))

      (test-query-string-search :actor "description" :description)

      (testing "GET /ctia/actor/external_id/:external_id"
        (let [response (get (format "ctia/actor/external_id/%s"
                                    (encode (rand-nth actor-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              actors (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc actor :id (id/long-id actor-id))]
               actors))))

      (testing "PUT /ctia/actor/:id"
        (let [with-updates (assoc actor
                                  :title "modified actor")
              response (put (str "ctia/actor/" (:short-id actor-id))
                            :body with-updates
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               with-updates
               updated-actor))))

      (testing "PUT invalid /ctia/actor/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/actor/" (:short-id actor-id))
                   :body (assoc actor
                                :title (clojure.string/join
                                        (repeatedly 1025 (constantly \0))))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/actor/:id"
        (let [response (delete (str "ctia/actor/" (:short-id actor-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/actor/" (:short-id actor-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/actor"
    (let [{status :status
           body :body}
          (post "ctia/actor"
                ;; This field has an invalid length
                :body (assoc new-actor-minimal
                             :title (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-actor-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/actor"
                            :body (-> new-actor-maximal
                                      (dissoc :id)
                                      (assoc :source (str "dotimes " %)
                                             :title "foo"))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/actor/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     actor-sort-fields)

    (field-selection-tests
     ["ctia/actor/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     actor-sort-fields)))

(deftest-for-each-store test-actor-routes-access-control
  (access-control-test "actor"
                       new-actor-minimal
                       true
                       true))
