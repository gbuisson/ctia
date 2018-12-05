(ns ctia.logger-test
  (:require [ctia.events :as e]
            [ctia.test-helpers
             [core :as test-helpers]
             [es :as es-helpers]]
            [ctia.entity.event.obj-to-event :as o2e]
            [clojure.test :as t :refer :all]
            [schema.test :as st]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(use-fixtures :once st/validate-schemas)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-properties:events-logging
                                    test-helpers/fixture-ctia-fast]))

(deftest test-logged
  (let [sb (StringBuilder.)
        patched-log (fn [logger
                         level
                         throwable
                         message]
                      (.append sb message)
                      (.append sb "\n"))]
    (with-redefs [log/log* patched-log]
      (e/send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-1"
                      :type :test
                      :tlp "green"
                      :data 1}
                     "test-1"))
      (e/send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-2"
                      :type :test
                      :tlp "green"
                      :data 2}
                     "test-2"))
      (Thread/sleep 100)   ;; wait until the go loop is done
      (let [scrubbed (-> (str sb)
                         (str/replace #"#inst \"[^\"]*\"" "#inst \"\"")
                         (str/replace #":id event[^,]*" ":id event"))
            expected
            "event: {:owner tester, :groups [foo], :entity {:owner tester, :groups [foo], :id test-1, :type :test, :tlp green, :data 1}, :timestamp #inst \"\", :id test-1, :type event, :tlp green, :event_type :record-created}\nevent: {:owner tester, :groups [foo], :entity {:owner tester, :groups [foo], :id test-2, :type :test, :tlp green, :data 2}, :timestamp #inst \"\", :id test-2, :type event, :tlp green, :event_type :record-created}\n"]
        (is (= expected scrubbed))))))
