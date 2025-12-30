(ns cadro.model.scale
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(db/register-schema!
 {::controller {:db/cardinality :db.cardinality/one
                :db/valueType :db.type/ref}
  ::scale      {:db/cardinality :db.cardinality/one
                :db/valueType :db.type/ref}})

;Defined in scale-controller for dependency issue
;(s/def ::controller :cadro.model.scale-controller/scale-controller)
(s/def ::raw-value number?)

(s/def ::scale (s/keys :req [::object/id
                             ::object/display-name
                             ::controller
                             ::raw-value]))

(defn upsert-scale-value-tx
  [ds controller-id scale-name value]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::object/id ?id]
                             [?e ::object/display-name ?name]
                             [?e ::controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::object/id           (or (get name->id scale-name)
                                (db/squuid))
      ::object/display-name scale-name
      ::raw-value           value
      ::controller          controller-id}]))
