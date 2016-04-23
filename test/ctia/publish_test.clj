(ns ctia.publish-test
  (:require [clojure.test :refer :all]
            [ctia.events :as e]
            [ctia.subscribe :as sub]
            [ctia.lib.async :as la]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pget]
            [ctia.stores.redis.store :as store]
            [ctia.test-helpers.core :as test-helpers]
            [taoensso.carmine :as c]
            [clojure.core.async :as a])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once test-helpers/fixture-properties:redis-store)
(use-fixtures :each test-helpers/fixture-ctia)

(deftest ^:integration test-publish-connection
  (testing "Checking that Redis can be connected to"
    (lr/publish (pget/redis-host-port* @properties)
                "test-channel"
                42)))

(deftest ^:integration test-events
  (testing "Checking publications into redis get picked up by a subscription"
    (let [results (atom [])
          finish-signal (CountDownLatch. 3)
          sub (sub/event-subscribe (fn [ev]
                                     (swap! results conj ev)
                                     (.countDown finish-signal)))]
      (e/send-create-event "tester" {} "TestModelType" {:data 1})
      (e/send-event {:owner "tester" :http-params {} :model {:data 2}})
      (e/send-event {:owner "tester" :http-params {} :model {:data 3}})
      (is (.await finish-signal 10 TimeUnit/SECONDS)
          "Unexpected timeout waiting for subscriptions")
      (is (= [1 2 3] (map (comp :data :model) @results)))
      (la/close! sub))))
