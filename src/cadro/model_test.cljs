(ns cadro.model-test
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.model.scale-controller :as scale-controller]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]))

(deftest t-set-reference?-tx
  (let [conn     (d/create-conn (db/schema))
        refs     (fn [db]
                   (into #{} (d/q '[:find [?id ...]
                                    :where
                                    [?p ::model/reference? true]
                                    [?p ::model/id ?id]]
                                   db)))
        machine1 (db/squuid)
        point1   (db/squuid)
        point2   (db/squuid)
        point3   (db/squuid)
        _        (d/transact! conn [{::model/id         point1
                                     ::model/position   {"X" 42}}
                                    {::model/id         point2
                                     ::model/position   {"X" 107}
                                     ::model/reference? true}
                                    {::model/id         point3
                                     ::model/position   {"X" 99}}])
        tx       (model/set-reference?-tx @conn [::model/id point1])
        _        (d/transact! conn tx)
        db1      @conn
        tx       (model/set-reference?-tx @conn [::model/id point1])
        _        (d/transact! conn tx)
        db2      @conn]
    (is (= #{point1} (refs db1))
        "It sets an existing non-reference point to be reference.")
    (is (= #{point1} (refs db2))
        "An existing reference point is still reference.")))

(defn- associated?
  [db locus-id axis-name]
  (let [result (d/q '[:find ?scale-id .
                      :in $ ?locus-id ?axis-name
                      :where
                      [?locus-id ::model/spans ?scale-id]
                      [?scale-id ::model/display-name ?axis-name]]
                    db
                    locus-id
                    axis-name)]
    (boolean result)))

(deftest t-associate-dissociate-scale-tx
  (let [conn            (d/create-conn (db/schema))
        tx              (scale-controller/add-controllers-tx @conn [{::model/display-name      "HC-01"
                                                                     ::scale-controller/address "00:00:01"}])
        _               (d/transact! conn tx)
        tx              (scale-controller/add-received-data-tx @conn [::scale-controller/address "00:00:01"] "X150;")
        _               (d/transact! conn tx)
        scale-id        (d/q '[:find ?scale .
                               :where
                               [?scale ::model/display-name "X"]
                               [?scale ::model/controller ?controller]
                               [?controller ::scale-controller/address "00:00:01"]]
                             @conn)
        {:keys [id tx]} (locus/new-machine-tx @conn)
        _               (d/transact! conn tx)
        db1             @conn
        tx              (model/associate-scale-tx @conn id scale-id)
        _               (d/transact! conn tx)
        db2             @conn
        tx              (model/associate-scale-tx @conn id scale-id)
        _               (d/transact! conn tx)
        db3             @conn
        tx              (model/dissociate-scale-tx @conn id scale-id)
        _               (d/transact! conn tx)
        db4             @conn]
    (is (not (associated? db1 id "X")))
    (is (associated? db2 id "X"))
    (is (associated? db3 id "X"))
    (is (not (associated? db4 id "X")))))
