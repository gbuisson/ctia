(ns ctia.stores.es.investigation
  (:require
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core
    :refer [StoredInvestigation PartialStoredInvestigation]]))

(def handle-create (crud/handle-create :investigation StoredInvestigation))
(def handle-read (crud/handle-read :investigation PartialStoredInvestigation))
(def handle-update (crud/handle-update :investigation StoredInvestigation))
(def handle-delete (crud/handle-delete :investigation StoredInvestigation))
(def handle-list (crud/handle-find :investigation PartialStoredInvestigation))
(def handle-query-string-search (crud/handle-query-string-search
                                 :investigation PartialStoredInvestigation))
