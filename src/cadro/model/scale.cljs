(ns cadro.model.scale
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(defn upsert-scale-value-tx
  [ds controller-id scale-name value]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::model/id ?id]
                             [?e ::model/display-name ?name]
                             [?e ::model/controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::model/id           (or (get name->id scale-name)
                               (db/squuid))
      ::model/display-name scale-name
      ::model/raw-count    value
      ::model/controller   controller-id}]))
