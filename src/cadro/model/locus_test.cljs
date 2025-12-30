(ns cadro.model.locus-test
  (:require
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
   [cadro.model.scale-controller :as scale-controller]
   [cadro.testing.db :as tdb]
   [clojure.test :refer [deftest is]]
   [datascript.core :as d]))

(defn- associated?
  [db locus-id axis-name]
  (let [result (d/q '[:find ?assoc-id .
                      :in $ ?locus-id ?axis-name
                      :where
                      [?locus-id ::locus/locus-scale ?assoc-id]
                      [?assoc-id ::scale/scale ?scale-id]
                      [?scale-id ::object/display-name ?axis-name]]
                    db
                    locus-id
                    axis-name)]
    (boolean result)))

(deftest t-associate-scale-tx
  (let [conn            (tdb/conn
                         #(scale-controller/add-controllers-tx % [{::object/display-name      "HC-01"
                                                                   ::scale-controller/address "00:00:01"}])
                         #(scale-controller/add-received-data-tx % [::scale-controller/address "00:00:01"] "X150;"))
        {:keys [id tx]} (locus/new-machine-tx)
        _               (d/transact! conn tx)
        db              @conn
        scale-id        (d/q '[:find ?scale .
                               :where
                               [?scale ::object/display-name "X"]
                               [?scale ::scale/controller ?controller]
                               [?controller ::scale-controller/address "00:00:01"]]
                             @conn)
        tx              (locus/associate-scale-tx @conn id scale-id)
        _               (d/transact! conn tx)
        db'             @conn]
    (is (not (associated? db id "X")))
    (is (associated? db' id "X"))))
