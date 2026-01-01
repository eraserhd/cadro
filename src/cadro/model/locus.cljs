(ns cadro.model.locus
  "Model of loci, or positions (potentially with transformations)."
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(defn new-machine-tx
  [ds]
  (let [machine-id (db/squuid)
        point-id   (db/squuid)]
    {:id [::model/id machine-id]
     :tx (concat
          [{::model/id           machine-id
            ::model/display-name "New Machine"
            ::model/transforms   [{::model/id point-id
                                   ::model/display-name "Origin"
                                   ::model/position {}}]}]
          (model/set-reference?-tx ds [::model/id point-id]))}))

(defn associate-scale-tx
  [ds locus-id scale-id]
  [[:db/add locus-id ::model/spans scale-id]])

(defn dissociate-scale-tx
  [ds locus-id scale-id]
  [[:db/retract locus-id ::model/spans scale-id]])
