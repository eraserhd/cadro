(ns cadro.model.scale
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
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

(s/def ::scale (s/keys :req [::model/id
                             ::model/display-name
                             ::controller
                             ::raw-value]))

(defn upsert-scale-value-tx
  [ds controller-id scale-name value]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::model/id ?id]
                             [?e ::model/display-name ?name]
                             [?e ::controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::model/id           (or (get name->id scale-name)
                               (db/squuid))
      ::model/display-name scale-name
      ::raw-value          value
      ::controller         controller-id}]))
