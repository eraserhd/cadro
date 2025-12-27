(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(db/register-schema!
 {::address {:db/cardinality :db.cardinality/one}
  ::status  {:db/cardinality :db.cardinality/one}})

(s/def ::address string?)
(s/def ::status #{:disconnected
                  :connecting
                  :connected})

(s/def ::scale-controller (s/keys :req [::object/id
                                        ::object/display-name
                                        ::address
                                        ::status]))

(defn set-status-tx
  [device-id status]
  {:pre [(s/assert ::status status)]}
  [[:db/add device-id ::status status]])

(defn data-received-tx
  [ds device-id data]
  [])
