(ns cadro.model-test
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
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
      "It sets an existing non-reference point to be reference."
      [{::model/id         (t/id :point1)
        ::model/position   {"X" 42}}
       {::model/id         (t/id :point2)
        ::model/position   {"X" 107}
        ::model/reference? true}
       {::model/id         (t/id :point3)
        ::model/position   {"X" 99}}]
      [#'model/set-reference?-tx [::model/id (t/id :point1)]]
      (fn [{:keys [db]}]
        (is (= #{(t/id :point1)} (refs db)))))

    (t/scenario
      "Idempotency - an existing reference point is still a reference."
      [{::model/id         (t/id :point1)
        ::model/position   {"X" 42}}
       {::model/id         (t/id :point2)
        ::model/position   {"X" 107}
        ::model/reference? true}
       {::model/id         (t/id :point3)
        ::model/position   {"X" 99}}]
      [#'model/set-reference?-tx [::model/id (t/id :point1)]]
      [#'model/set-reference?-tx [::model/id (t/id :point1)]]
      (fn [{:keys [db]}]
        (is (= #{(t/id :point1)} (refs db)))))))

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
    [{::model/id           (t/id :scale/X)
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       (t/id :point1)
                            ::model/position {}}}]
    (fn [{:keys [db]}]
      (is (not (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)])))))

  (t/scenario
    "A scale can be associated."
    [{::model/id           (t/id :scale/X)
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       (t/id :point1)
                            ::model/position {}}}]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))

  (t/scenario
    "Association is idempotent."
    [{::model/id           (t/id :scale/X)
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       (t/id :point1)
                            ::model/position {}}}]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))

  (t/scenario
    "Scales can be dissociated."
    [{::model/id           (t/id :scale/X)
      ::model/display-name "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/display-name "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/display-name "New Machine"
      ::model/transforms   {::model/id       (t/id :point1)
                            ::model/position {}}
      ::model/spans        [::model/id (t/id :scale/X)]}]
   [#'model/dissociate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (not (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)])))))

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
                  _               (d/transact! conn (model/add-controllers-tx
                                                     @conn
                                                     [{::model/display-name "HC-01"
                                                       ::model/hardware-address "00:00:01"}]))
                  data            (->> scales
                                       (map (fn [[axis-name value]]
                                              (str axis-name value ";")))
                                       (apply str))
                  _               (d/transact! conn (model/add-received-data-tx
                                                     @conn
                                                     [::model/hardware-address "00:00:01"]
                                                     data))
                  {:keys [id tx]} (model/new-machine-tx @conn)
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

(deftest t-add-controllers-tx
  (let [conn (d/create-conn (db/schema))
        tx   (model/add-controllers-tx @conn [{::model/display-name      "Nexus 7"
                                               ::model/hardware-address "00:00:01"}
                                              {::model/display-name      "HC-06"
                                               ::model/hardware-address "02:03:04"}])
        _    (d/transact! conn tx)
        pull [::model/id
              ::model/display-name
              ::model/hardware-address
              ::model/connection-status]
        c1   (d/pull @conn pull [::model/hardware-address "00:00:01"])
        c2   (d/pull @conn pull [::model/hardware-address "02:03:04"])
        tx2  (model/add-controllers-tx @conn [{::model/display-name       "Nexus 7 Renamed"
                                               ::model/hardware-address  "00:00:01"}])
        _    (d/transact! conn tx2)
        c1'  (d/pull @conn pull [::model/hardware-address "00:00:01"])]
    (is (= {::model/display-name "Nexus 7"
            ::model/hardware-address "00:00:01"
            ::model/connection-status :disconnected}
           (dissoc c1 ::model/id))
        "It stores the 'Nexus 7' controller, marking as disconnected.")
    (is (= {::model/display-name "HC-06"
            ::model/hardware-address "02:03:04"
            ::model/connection-status :disconnected}
           (dissoc c2 ::model/id))
        "It stores the 'HC-06' controller, marking as disconnected.")
    (is (uuid? (::model/id c1))
        "It creates a UUID for 'Nexus 7'.")
    (is (uuid? (::model/id c2))
        "It creates a UUID for 'HC-06'.")
    (is (= "Nexus 7 Renamed" (::model/display-name c1'))
        "It updates a name when a new one is received.")
    (is (= (::model/id c1) (::model/id c1'))
        "It does not update a UUID.")))

(defn- after-receives
  [& receives]
  (let [controller-id [::model/hardware-address "00:00:01"]
        conn          (d/create-conn (db/schema))
        tx            (model/add-controllers-tx @conn [{::model/display-name "HC-06"
                                                        ::model/hardware-address "00:00:01"}])
        _             (d/transact! conn tx)
        _             (doseq [data receives]
                        (let [tx (model/add-received-data-tx @conn controller-id data)]
                          (d/transact! conn tx)))]
    (d/entity @conn controller-id)))

(deftest t-add-received-data-tx
  (let [controller (after-receives "X150;Y250;Z350;T72;\n")]
    (is (= #{{::model/display-name "X"
              ::model/raw-count 150}
             {::model/display-name "Y"
              ::model/raw-count 250}
             {::model/display-name "Z"
              ::model/raw-count 350}
             {::model/display-name "T"
              ::model/raw-count 72}}
           (->> (::model/_controller controller)
                (map #(select-keys % [::model/display-name ::model/raw-count]))
                (into #{})))
        "It creates scales and stores raw values on receipt.")
    (is (every? (comp uuid? ::model/id) (::model/_controller controller))
        "Every new scale is assigned a uuid.")
    (is (= 4 (count (map ::model/id (::model/_controller controller))))
        "The new uuids are unique."))
  (let [controller (after-receives "X150;\n" "X152;\n")]
    (is (= #{{::model/display-name "X"
              ::model/raw-count 152}}
           (->> (::model/_controller controller)
                (map #(select-keys % [::model/display-name ::model/raw-count]))
                (into #{})))
        "It updates existing scale values."))
  (testing "partial receives"
    (doseq [:let [full-data "X150;Y250;Z350;T72;\n"]
            i (range (count full-data))]
      (let [a          (subs full-data 0 i)
            b          (subs full-data i)
            controller (after-receives a b)]
        (is (= #{{::model/display-name "X"
                  ::model/raw-count 150}
                 {::model/display-name "Y"
                  ::model/raw-count 250}
                 {::model/display-name "Z"
                  ::model/raw-count 350}
                 {::model/display-name "T"
                  ::model/raw-count 72}}
               (->> (::model/_controller controller)
                    (map #(select-keys % [::model/display-name ::model/raw-count]))
                    (into #{})))
            (str "It correctly processes '" a "' then '" b "'."))))))
