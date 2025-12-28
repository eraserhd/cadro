(ns cadro.model.scale
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(db/register-schema!
 {::controller {:db/cardinality :db.cardinality/one
                :db/valueType :db.type/ref}})

(s/def ::controller :cadro.model.scale-controller/scale-controller)
(s/def ::raw-value number?)

(s/def ::scale (s/keys :req [::object/id
                             ::object/display-name
                             ::controller
                             ::raw-value]))

(defn upsert-scale-value-tx
  [ds controller-id scale-name value]
  [{::object/id                   (db/squuid)
    ::object/display-name         scale-name
    :cadro.model.scale/raw-value  value
    :cadro.model.scale/controller controller-id}])
