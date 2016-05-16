(ns ctia.task.es.reindex-es-index
  (:require [clojurewerkz.elastisch.rest
             [bulk :refer [bulk-create bulk-with-index]]
             [document :as document]]
            [ctia.lib.es.index :refer [connect create! index-exists?-fn]]
            [ctia.stores.es.mapping :refer [store-mappings]]))


(defn transform [_type source]
  "apply any source transformations here based on type"
  source)

(defn prepare-one [{:keys [_source _type _id]}]
  "prepare one element of the batch"
  (assoc (transform _type _source) :_type _type :_id _id))

(defn prepare-batch [batch]
  "format a batch of documents for ingestion"
  (let [hits (get-in batch [:hits :hits] [])]
    (map prepare-one hits)))

(defn get-batch
  "Get a batch of documents from the supplied index"
  [conn index-name batch-size offset]

  (prepare-batch
   (document/search-all-types conn index-name {:size batch-size
                                               :from offset})))

(defn ingest-batch [conn index-name batch]
  "Ingest documents to the supplied index"
  (bulk-with-index conn index-name (bulk-create batch)))

(defn reindex-store [uri index-name new-index-name batch-size]
  (let [conn (connect {:uri uri})]
    (assert ((index-exists?-fn conn) conn index-name)
            "The supplied origin index does not exists.")

    (println "Creating destination index...")
    (create! conn new-index-name store-mappings)

    (loop [offset 0]
      (let [batch (get-batch conn index-name batch-size offset)]
        (if-not (empty? batch)
          (do (println "reingest from" offset batch-size "documents")
              (ingest-batch conn index-name batch)
              (recur (+ offset batch-size)))
          (println "reindex complete"))))))

(defn -main [& args]
  (apply reindex-store args))
