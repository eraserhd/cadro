(ns cadro.model-test
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.session :as session]
   [cadro.test :as t]
   [clara.rules :as clara]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]))

(deftest t-set-reference
  (let [id      (random-uuid)
        session (-> session/base-session
                    (clara/insert (model/asserted id ::model/coordinates {}))
                    (model/set-reference id)
                    (clara/fire-rules))]
    (is (= id (model/reference session))
        "Can retrieve current reference.")
    (is (empty? (model/errors session))
        "No invariant errors"))
  (let [id1     (random-uuid)
        id2     (random-uuid)
        session (-> session/base-session
                    (clara/insert (model/asserted id1 ::model/coordinates {}))
                    (clara/insert (model/asserted id2 ::model/coordinates {}))
                    (model/set-reference id1)
                    (model/set-reference id2)
                    (clara/fire-rules))]
    (is (= id2 (model/reference session))
        "Updates current reference.")
    (is (empty? (model/errors session))
        "No invariant errors"))
  (let [id1     (random-uuid)
        id2     (random-uuid)
        session (-> session/base-session
                    (clara/insert (model/asserted id1 ::model/coordinates {}))
                    (clara/insert (model/asserted id2 ::model/coordinates {}))
                    (clara/insert (model/asserted id1 ::model/reference? true))
                    (clara/insert (model/asserted id2 ::model/reference? true))
                    (clara/fire-rules))]
    (is (= [(model/->InvariantError "more than one reference point in session" {:count 2})]
           (model/errors session))))
  (let [id      (random-uuid)
        session (-> session/base-session
                    (model/set-reference id)
                    (clara/fire-rules))]
    (is (= [(model/->InvariantError "reference point does not have coordinates" {:id id})]
           (model/errors session)))))

(defn- associated?
  [db fixture-id scale-id]
  (let [result (d/q '[:find ?scale-id .
                      :in $ ?fixture-id ?scale-id
                      :where
                      [?fixture-id ::model/spans ?scale-id]]
                    db
                    fixture-id
                    scale-id)]
    (boolean result)))

(deftest t-associate-dissociate-scale-tx
  (t/scenario
    "Scales aren't automatically associated with machines."
    [{::model/id           (t/id :scale/X)
      ::model/displays-as "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/displays-as "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/displays-as "New Machine"
      ::model/transforms   {::model/id          (t/id :point1)
                            ::model/coordinates {}}}]
    (fn [{:keys [db]}]
      (is (not (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)])))))

  (t/scenario
    "A scale can be associated."
    [{::model/id           (t/id :scale/X)
      ::model/displays-as "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/displays-as "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/displays-as "New Machine"
      ::model/transforms   {::model/id          (t/id :point1)
                            ::model/coordinates {}}}]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))

  (t/scenario
    "Association is idempotent."
    [{::model/id           (t/id :scale/X)
      ::model/displays-as "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/displays-as "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/displays-as "New Machine"
      ::model/transforms   {::model/id          (t/id :point1)
                            ::model/coordinates {}}}]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   [#'model/associate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))

  (t/scenario
    "Scales can be dissociated."
    [{::model/id           (t/id :scale/X)
      ::model/displays-as "X"
      ::model/raw-count    150
      ::model/controller   {::model/id (t/id :controller)
                            ::model/displays-as "HC-01"
                            ::model/hardware-address "00:00:01"}}
     {::model/id           (t/id :machine1)
      ::model/displays-as "New Machine"
      ::model/transforms   {::model/id          (t/id :point1)
                            ::model/coordinates {}}
      ::model/spans        [::model/id (t/id :scale/X)]}]
   [#'model/dissociate-scale-tx [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]]
   (fn [{:keys [db]}]
     (is (not (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))))

(deftest t-insert-controllers
  (let [session      (-> session/base-session
                         (model/insert-controllers [{::model/displays-as      "Nexus 7"
                                                     ::model/hardware-address "00:00:01"}
                                                    {::model/displays-as      "HC-06"
                                                     ::model/hardware-address "02:03:04"}])
                         clara/fire-rules)
        controllers  (->> (clara/query session model/controllers)
                          (sort-by :?hardware-address))
        session'     (-> session
                         (model/insert-controllers [{::model/displays-as      "Nexus 7 Renamed"
                                                     ::model/hardware-address "00:00:01"}])
                         clara/fire-rules)
        controllers' (->> (clara/query session' model/controllers)
                          (sort-by :?hardware-address))]
    (is (= [{:?displays-as       "Nexus 7"
             :?hardware-address  "00:00:01"
             :?connection-status :disconnected}
            {:?displays-as       "HC-06"
             :?hardware-address  "02:03:04"
             :?connection-status :disconnected}]
           (map #(dissoc % :?id) controllers))
        "It stores new controllers, defaulting to disconnected.")
    (is (every? uuid? (map :?id controllers))
        "It creates a UUID for every controller.")
    (is (= "Nexus 7 Renamed" (-> controllers' first :?displays-as))
        "It updates a name when a new one is received.")
    (is (= (-> controllers first :?id) (-> controllers' first :?id))
        "It does not update a UUID.")))

(defn- after-receives
  [& receives]
  (let [controller-id [::model/hardware-address "00:00:01"]
        conn          (d/create-conn (db/schema))
        tx            (model/add-controllers-tx @conn [{::model/displays-as "HC-06"
                                                        ::model/hardware-address "00:00:01"}])
        _             (d/transact! conn tx)
        _             (doseq [data receives]
                        (let [tx (model/add-received-data-tx @conn controller-id data)]
                          (d/transact! conn tx)))]
    (d/entity @conn controller-id)))

(deftest t-add-received-data-tx
  (let [controller (after-receives "X150;Y250;Z350;T72;\n")]
    (is (= #{{::model/displays-as "X"
              ::model/raw-count 150}
             {::model/displays-as "Y"
              ::model/raw-count 250}
             {::model/displays-as "Z"
              ::model/raw-count 350}
             {::model/displays-as "T"
              ::model/raw-count 72}}
           (->> (::model/_controller controller)
                (map #(select-keys % [::model/displays-as ::model/raw-count]))
                (into #{})))
        "It creates scales and stores raw values on receipt.")
    (is (every? (comp uuid? ::model/id) (::model/_controller controller))
        "Every new scale is assigned a uuid.")
    (is (= 4 (count (map ::model/id (::model/_controller controller))))
        "The new uuids are unique."))
  (let [controller (after-receives "X150;\n" "X152;\n")]
    (is (= #{{::model/displays-as "X"
              ::model/raw-count 152}}
           (->> (::model/_controller controller)
                (map #(select-keys % [::model/displays-as ::model/raw-count]))
                (into #{})))
        "It updates existing scale values."))
  (testing "partial receives"
    (doseq [:let [full-data "X150;Y250;Z350;T72;\n"]
            i (range (count full-data))]
      (let [a          (subs full-data 0 i)
            b          (subs full-data i)
            controller (after-receives a b)]
        (is (= #{{::model/displays-as "X"
                  ::model/raw-count 150}
                 {::model/displays-as "Y"
                  ::model/raw-count 250}
                 {::model/displays-as "Z"
                  ::model/raw-count 350}
                 {::model/displays-as "T"
                  ::model/raw-count 72}}
               (->> (::model/_controller controller)
                    (map #(select-keys % [::model/displays-as ::model/raw-count]))
                    (into #{})))
            (str "It correctly processes '" a "' then '" b "'."))))))
