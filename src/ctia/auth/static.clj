(ns ctia.auth.static
  (:require [clj-momo.lib.set :refer [as-set]]
            [clojure
             [set :as set]
             [string :as str]]
            [ctia
             [auth :refer [IIdentity IAuth] :as auth]
             [properties :as p]]))

(def ^:private write-capabilities
  (set/difference auth/all-capabilities
                  #{:specify-id}))

(def ^:private read-only-capabilities
  (set/difference (->> auth/all-capabilities
                       (remove (fn [cap]
                                 (some #(str/starts-with? (name cap) %)
                                       ["create" "delete"])))
                       set)
                  #{:developer
                    :specify-id}))

(defrecord WriteIdentity [name guid]
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    name)
  (groups [_]
    (remove nil? [guid]))
  (allowed-capabilities [_]
    write-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 write-capabilities)))

(defrecord ReadOnlyIdentity []
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    auth/not-logged-in-owner)
  (groups [_]
    (remove nil? auth/not-logged-in-groups))
  (allowed-capabilities [_]
    read-only-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 read-only-capabilities)))

(defrecord AuthService [auth-config]
  IAuth
  (identity-for-token [_ token]
    (if (= token (get-in auth-config [:static :secret]))
      (->WriteIdentity (get-in auth-config [:static :name])
                       (get-in auth-config [:static :group]))
      (->ReadOnlyIdentity))))
