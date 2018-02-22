(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require [ctim.examples.sightings
             :refer [new-sighting-maximal]]
            [ctia.schemas.sorting
             :refer [sighting-sort-fields]]
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
             [auth :refer [all-capabilities]]
             [access-control :refer [access-control-test]]
             [core :as helpers :refer [delete get post put url-id]]
             [fake-whoami-service :as whoami-helpers]
             [pagination :refer [pagination-test]]
             [field-selection :refer [field-selection-tests]]
             [http :refer [api-key]]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.sightings
             :refer [new-sighting-minimal
                     new-sighting-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key
                                      "foouser"
                                      "foogroup"
                                      "user")
  (testing "POST /ctia/sighting"
    (let [new-sighting (-> new-sighting-maximal
                           (dissoc :id)
                           (assoc
                            :tlp "green"
                            :external_ids
                            ["http://ex.tld/ctia/sighting/sighting-123"
                             "http://ex.tld/ctia/sighting/sighting-345"]))
          {status :status
           sighting :parsed-body}
          (post "ctia/sighting"
                :body new-sighting
                :headers {"Authorization" api-key})

          sighting-id (id/long-id->id (:id sighting))
          sighting-external-ids (:external_ids sighting)]
      (is (empty? (:errors sighting)) "No errors when")
      (is (= 201 status))
      (is (deep=
           (assoc new-sighting :id (id/long-id sighting-id))
           sighting))

      (testing "the sighting ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    sighting-id)      (:hostname    show-props)))
          (is (= (:protocol    sighting-id)      (:protocol    show-props)))
          (is (= (:port        sighting-id)      (:port        show-props)))
          (is (= (:path-prefix sighting-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/sighting/external_id/:external_id"
        (let [response (get (format "ctia/sighting/external_id/%s"
                                    (encode (rand-nth sighting-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              sightings (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc new-sighting :id (id/long-id sighting-id))]
               sightings))))

      (test-query-string-search :sighting "a sighting" :description)

      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:short-id sighting-id))
                   :headers {"Authorization" api-key})]
          (is (empty? (:errors sighting)) "No errors when")
          (is (= 200 status))
          (is (deep=
               (assoc new-sighting :id (id/long-id sighting-id))
               sighting))))

      (testing "PUT /ctia/sighting/:id"
        (let [with-updates (assoc sighting :title "updated sighting")
              {status :status
               updated-sighting :parsed-body}
              (put (str "ctia/sighting/" (:short-id sighting-id))
                   :body with-updates
                   :headers {"Authorization" api-key})]
          (is (empty? (:errors sighting)) "No errors when updating sighting")
          (is (= 200 status))
          (is (deep=
               with-updates
               updated-sighting))))

      (testing "PUT invalid /ctia/sighting/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/sighting/" (:short-id sighting-id))
                   :body
                   (assoc sighting
                          :title (clojure.string/join
                                  (repeatedly 1025 (constantly \0))))
                   :headers {"Authorization" api-key})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/sighting/:id"
        (let [{status :status} (delete (str "ctia/sighting/" (:short-id sighting-id))
                                       :headers {"Authorization" api-key})]
          (is (= 204 status))
          (let [{status :status} (get (str "ctia/sighting/" (:short-id sighting-id))
                                      :headers {"Authorization" api-key})]
            (is (= 404 status)))))))

  (testing "POST invalid /ctia/sighting"
    (let [{status :status
           body :body}
          (post "ctia/sighting"
                :body (assoc new-sighting-minimal
                             ;; This field has an invalid length
                             :title (apply str (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-sighting-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/sighting"
                            :body (-> new-sighting-maximal
                                      (dissoc :id :relations)
                                      (assoc :source (str "dotimes " %)))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/sighting/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     sighting-sort-fields)

    (field-selection-tests
     ["ctia/sighting/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     sighting-sort-fields)))

(deftest-for-each-store test-sighting-routes-access-control
  (access-control-test "sighting"
                       new-sighting-minimal
                       true
                       true))
