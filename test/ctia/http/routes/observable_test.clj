(ns ctia.http.routes.observable-test
  (:refer-clojure :exclude [get])
  (:require

   [schema-generators.generators :as g]
   [ctia.schemas.sighting  :refer [NewSighting]]
   [ctia.schemas.indicator  :refer [NewIndicator]]
   [ctia.schemas.judgement  :refer [NewJudgement]]

   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def api-key "45c1f5e3f05d0")
(defn redprintln [& s]
  (print "\u001b[31m")
  (apply println s)
  (print "\u001b[0m"))
(defn test-post
  "Helper which test a post request occurs with success and return the right object"
  [path new-entity]
  (testing (str "POST " path)
    (let [resp (post path :body new-entity :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (redprintln (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (redprintln (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= new-entity (dissoc (:parsed-body resp) :id :created :modified :owner)))
        (:parsed-body resp)))))

(defn test-get
  "Helper which test a get request occurs with success and return the right object"
  [path expected-entity]
  (testing (str "GET " path)
    (let [resp (get path :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (redprintln (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (redprintln (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= expected-entity (:parsed-body resp)))
        (:parsed-body resp)))))

(deftest-for-each-store test-get-things-by-observable-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [nb-judgements 1
        nb-indicators 1
        nb-sightings 1
        observable {:type "ip" :value "1.2.3.4"}]
    (doseq [new-judgement (->> (g/sample nb-judgements NewJudgement)
                               ;; HARDCODED VALUES
                               (map #(merge % {:disposition 5
                                               :disposition_name "Unknown"
                                               ;; TODO: empty value isn't supported
                                               :observable observable})))]
      (let [judgement (test-post "ctia/judgement" new-judgement)]
        (when judgement
          (let [new-indicators (->> (g/sample nb-indicators NewIndicator)
                                    (map #(merge % {:observable observable})))
                indicators     (remove nil?
                                       (map #(test-post "ctia/indicator" %)
                                            new-indicators))]
            (when (= (count indicators) nb-indicators)
              (let [add-sightings-fn #(assoc %
                                             :indicators
                                             (map (fn [i] {:indicator_id (:id i)})
                                                  indicators)
                                             :observables [observable])
                    new-sightings  (->> (g/sample nb-sightings NewSighting)
                                        (map add-sightings-fn))
                    sightings      (doall (map #(test-post "ctia/sighting" %)
                                               new-sightings))
                    route-pref (str "ctia/" (get-in judgement [:observable :type])
                                    "/" (get-in judgement [:observable :value]))]
                (test-get (str route-pref "/judgements") [judgement])
                ;; TODO:
                ;; (test-get (str route-pref "/indicators") indicators)
                (test-get (str route-pref "/sightings") sightings)))))))))

