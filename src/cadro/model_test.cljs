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
    (is (not (associated? db4 id "X")))

    #_
    (testing "When a scale is associated, components are added to transformed positions"
      (let [machine-id
            (fn [db]
              (d/q '[:find ?machine-id .
                     :where
                     [?machine-id ::model/display-name "New Machine"]]
                   db))

            scale-id
            (fn [db axis-name]
              (d/q '[:find ?scale-id .
                     :in $ ?axis-name
                     :where
                     [?scale-id ::model/controller]
                     [?scale-id ::model/display-name ?axis-name]]
                   db
                   axis-name))

            assoc-axis
            (fn [conn axis-name]
              (d/transact! conn (model/associate-scale-tx @conn (machine-id @conn) (scale-id @conn axis-name)))
              conn)

            dissoc-axis
            (fn [conn axis-name]
              (d/transact! conn (model/dissociate-scale-tx @conn (machine-id @conn) (scale-id @conn axis-name)))
              conn)

            setup-machine
            (fn [{:keys [scales assocs points]}]
              (let [conn            (d/create-conn (db/schema))
                    _               (d/transact! conn (scale-controller/add-controllers-tx
                                                       @conn
                                                       [{::model/display-name "HC-01"
                                                         ::scale-controller/address "00:00:01"}]))
                    data            (->> scales
                                         (map (fn [[axis-name value]]
                                                (str axis-name value ";")))
                                         (apply str))
                    _               (d/transact! conn (scale-controller/add-received-data-tx
                                                       @conn
                                                       [::scale-controller/address "00:00:01"]
                                                       data))
                    {:keys [id tx]} (locus/new-machine-tx @conn)
                    _               (d/transact! conn tx)
                    _               (doseq [axis-name assocs]
                                      (assoc-axis conn axis-name))
                    old-points      (d/q '[:find [?eid ...]
                                           :where
                                           [?eid ::model/position]]
                                         @conn)
                    _               (d/transact! conn (for [op old-points]
                                                        [:db/retractEntity op]))
                    _               (d/transact! conn (for [[pname pos] points]
                                                        {::model/id (d/squuid)
                                                         ::model/display-name pname
                                                         ::model/position pos}))]
                conn))

            points
            (fn [conn]
              (->> (d/q '[:find ?name ?pos
                          :where
                          [?e ::model/display-name ?name]
                          [?e ::model/position ?pos]]
                        @conn)
                   (into {})))]
        (is (= {"P1" {"X" 150}}
               (-> (setup-machine {:scales {"X" 150}
                                   :assocs #{},
                                   :points {"P1" {}}})
                   (assoc-axis "X")
                   points))
            "Missing component is added to Origin when scale is associated with machine.")
        (is (= {"P1" {"X" 150}}
               (-> (setup-machine {:scales {"X" 205},
                                   :assocs #{"X"},
                                   :points {"P1" {"X" 150}}})
                   (dissoc-axis "X")
                   points))
            "Component stays after scale is dissociated with machine.")
        (is (= {"P1" {"X" 150, "Y" 79}}
               (-> (setup-machine {:scales {"X" 42
                                            "Y" 79},
                                   :assocs #{"X"}
                                   :points {"P1" {"X" 150}}})
                   (assoc-axis "X")
                   points))
            "Existing component is not overridden when scale is associated with machine.")))))
