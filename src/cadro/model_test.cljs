(ns cadro.model-test
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.model.scale-controller :as scale-controller]
   [cadro.test :as t]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]))

(deftest t-set-reference?-tx
  (let [refs (fn [db]
               (into #{} (d/q '[:find [?id ...]
                                :where
                                [?p ::model/reference? true]
                                [?p ::model/id ?id]]
                               db)))]
    (t/scenario
      [{::model/id         :uuid/point1
        ::model/position   {"X" 42}}
       {::model/id         :uuid/point2
        ::model/position   {"X" 107}
        ::model/reference? true}
       {::model/id         :uuid/point3
        ::model/position   {"X" 99}}]
      [#'model/set-reference?-tx [::model/id :uuid/point1]]
      (fn [{:keys [db]}]
        (is (= (t/d #{:uuid/point1}) (refs db))
            "It sets an existing non-reference point to be reference.")))

    (t/scenario
      [{::model/id         :uuid/point1
        ::model/position   {"X" 42}}
       {::model/id         :uuid/point2
        ::model/position   {"X" 107}
        ::model/reference? true}
       {::model/id         :uuid/point3
        ::model/position   {"X" 99}}]
      [#'model/set-reference?-tx [::model/id :uuid/point1]]
      [#'model/set-reference?-tx [::model/id :uuid/point1]]
      (fn [{:keys [db]}]
        (is (= (t/d #{:uuid/point1}) (refs db))
            "An existing reference point is still reference.")))))

(defn- associated?
  [db locus-id scale-id]
  (let [result (d/q '[:find ?scale-id .
                      :in $ ?locus-id ?scale-id
                      :where
                      [?locus-id ::model/spans ?scale-id]]
                    db
                    locus-id
                    scale-id)]
    (boolean result)))

(deftest t-associate-dissociate-scale-tx
  (t/scenario
    "Scales aren't automatically associated with machines."
    [{::model/id           :uuid/X
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id :uuid/controller
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           :uuid/machine1
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       :uuid/point1
                            ::model/position {}}}]
    (fn [{:keys [db :uuid/machine1 :uuid/X]}]
      (is (not (associated? db [::model/id machine1] [::model/id X])))))

  (t/scenario
    "A scale can be associated."
    [{::model/id           :uuid/X
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id :uuid/controller
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           :uuid/machine1
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       :uuid/point1
                            ::model/position {}}}]
   [#'model/associate-scale-tx [::model/id :uuid/machine1] [::model/id :uuid/X]]
   (fn [{:keys [db :uuid/machine1 :uuid/X]}]
     (is (associated? db [::model/id machine1] [::model/id X]))))

  (t/scenario
    "Association is idempotent."
    [{::model/id           :uuid/X
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id :uuid/controller
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           :uuid/machine1
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       :uuid/point1
                            ::model/position {}}}]
   [#'model/associate-scale-tx [::model/id :uuid/machine1] [::model/id :uuid/X]]
   [#'model/associate-scale-tx [::model/id :uuid/machine1] [::model/id :uuid/X]]
   (fn [{:keys [db :uuid/machine1 :uuid/X]}]
     (is (associated? db [::model/id machine1] [::model/id X]))))

  (t/scenario
    "Scales can be dissociated."
    [{::model/id           :uuid/X
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id :uuid/controller
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           :uuid/machine1
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       :uuid/point1
                            ::model/position {}}
      ::model/spans        [::model/id :uuid/X]}]
   [#'model/dissociate-scale-tx [::model/id :uuid/machine1] [::model/id :uuid/X]]
   (fn [{:keys [db :uuid/machine1 :uuid/X]}]
     (is (not (associated? db [::model/id machine1] [::model/id X])))))

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
                                                       ::model/hardware-address "00:00:01"}]))
                  data            (->> scales
                                       (map (fn [[axis-name value]]
                                              (str axis-name value ";")))
                                       (apply str))
                  _               (d/transact! conn (scale-controller/add-received-data-tx
                                                     @conn
                                                     [::model/hardware-address "00:00:01"]
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
          "Existing component is not overridden when scale is associated with machine."))))
