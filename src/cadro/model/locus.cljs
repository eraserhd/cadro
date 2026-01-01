(ns cadro.model.locus
  "Model of loci, or positions (potentially with transformations)."
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.scale :as scale]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(defn set-reference-tx
  [ds reference-id]
  (concat
    [[:db/add reference-id ::model/reference? true]]
    (for [eid (d/q '[:find [?eid ...]
                     :in $ ?reference-id
                     :where
                     [?eid ::model/reference? ?value]
                     (not [(= ?eid ?reference-id)])]
                   ds
                   reference-id)]
      [:db/retract eid ::model/reference?])))

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
          (set-reference-tx ds [::model/id point-id]))}))

(defn associate-scale-tx
  [ds locus-id scale-id]
  [[:db/add locus-id ::model/spans scale-id]])

(defn dissociate-scale-tx
  [ds locus-id scale-id]
  [[:db/retract locus-id ::model/spans scale-id]])
