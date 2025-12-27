(ns cadro.model.scale
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [cadro.model.scale-controller :as scale-controller]
   [clojure.spec.alpha :as s]))

(db/register-schema!
 {::controller {:db/cardinality :db.cardinality/one
                :db/valueType :db.type/ref}})

(s/def ::controller ::scale-controller/scale-controller)
(s/def ::raw-value number?)

(s/def ::scale (s/keys :req [::object/id
                             ::object/display-name
                             ::controller
                             ::raw-value]))
