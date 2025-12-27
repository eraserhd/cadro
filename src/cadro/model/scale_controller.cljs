(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(db/register-schema!
 {::address {:db/cardinality :db.cardinality/one}})

(s/def ::address string?)
(s/def ::connected? boolean?)

(s/def ::scale-controller (s/keys :req [::object/id
                                        ::object/display-name
                                        ::address
                                        ::connected?]))
