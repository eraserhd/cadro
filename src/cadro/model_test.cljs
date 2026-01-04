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
     (is (not (associated? db [::model/id (t/id :machine1)] [::model/id (t/id :scale/X)]))))))

(deftest t-propagate-spans
  (is (= [{::model/id         (t/id :machine1)
           ::model/transforms [{::model/id         (t/id :p1)
                                ::model/position   {(t/id :scale/X) 142
                                                    (t/id :scale/Y) 87
                                                    (t/id :scale/Z) -107}
                                ::model/spans      [{::model/id (t/id :scale/X) ::model/display-name "X"}
                                                    {::model/id (t/id :scale/Y) ::model/display-name "Y"}
                                                    {::model/id (t/id :scale/Z) ::model/display-name "Z"}]}
                               {::model/id         (t/id :p2)
                                ::model/position   {(t/id :scale/X) 196
                                                    (t/id :scale/Y) -101
                                                    (t/id :scale/Z) -98}
                                ::model/spans      [{::model/id (t/id :scale/X) ::model/display-name "X"}
                                                    {::model/id (t/id :scale/Y) ::model/display-name "Y"}
                                                    {::model/id (t/id :scale/Z) ::model/display-name "Z"}]
                                ::model/reference? true}
                               {::model/id         (t/id :p3)
                                ::model/position   {(t/id :scale/X) 67
                                                    (t/id :scale/Y) 111
                                                    (t/id :scale/Z) 82}
                                ::model/spans      [{::model/id (t/id :scale/X) ::model/display-name "X"}
                                                    {::model/id (t/id :scale/Y) ::model/display-name "Y"}
                                                    {::model/id (t/id :scale/Z) ::model/display-name "Z"}]}]
           ::model/spans [{::model/id (t/id :scale/X), ::model/display-name "X"}
                          {::model/id (t/id :scale/Y), ::model/display-name "Y"}
                          {::model/id (t/id :scale/Z), ::model/display-name "Z"}]}]
         (model/propagate-spans
           [{::model/id         (t/id :machine1)
             ::model/transforms [{::model/id         (t/id :p1)
                                  ::model/position   {(t/id :scale/X) 142
                                                      (t/id :scale/Y) 87
                                                      (t/id :scale/Z) -107}}
                                 {::model/id         (t/id :p2)
                                  ::model/position   {(t/id :scale/X) 196
                                                      (t/id :scale/Y) -101
                                                      (t/id :scale/Z) -98}
                                  ::model/reference? true}
                                 {::model/id         (t/id :p3)
                                  ::model/position   {(t/id :scale/X) 67
                                                      (t/id :scale/Y) 111
                                                      (t/id :scale/Z) 82}}]
             ::model/spans [{::model/id (t/id :scale/X), ::model/display-name "X"}
                            {::model/id (t/id :scale/Y), ::model/display-name "Y"}
                            {::model/id (t/id :scale/Z), ::model/display-name "Z"}]}]))))

(deftest t-add-distances
  (t/scenario
    "Adds distance to the reference point."
    [{::model/id         (t/id :machine1)
      ::model/transforms [{::model/id         (t/id :p1)
                           ::model/position   {(t/id :scale/X) 142
                                               (t/id :scale/Y) 87
                                               (t/id :scale/Z) -107}}
                          {::model/id         (t/id :p2)
                           ::model/position   {(t/id :scale/X) 196
                                               (t/id :scale/Y) -101
                                               (t/id :scale/Z) -98}
                           ::model/reference? true}
                          {::model/id         (t/id :p3)
                           ::model/position   {(t/id :scale/X) 67
                                               (t/id :scale/Y) 111
                                               (t/id :scale/Z) 82}}]
      ::model/spans [{::model/id (t/id :scale/X), ::model/display-name "X"}
                     {::model/id (t/id :scale/Y), ::model/display-name "Y"}
                     {::model/id (t/id :scale/Z), ::model/display-name "Z"}]}]
    (fn [{:keys [db]}]
      (is (= [{::model/id         (t/id :machine1)
               ::model/transforms [{::model/id         (t/id :p1)
                                    ::model/position   {(t/id :scale/X) 142
                                                        (t/id :scale/Y) 87
                                                        (t/id :scale/Z) -107}
                                    ::model/distance   {(t/id :scale/X) -54
                                                        (t/id :scale/Y) 188
                                                        (t/id :scale/Z) -9}}
                                   {::model/id         (t/id :p2)
                                    ::model/position   {(t/id :scale/X) 196
                                                        (t/id :scale/Y) -101
                                                        (t/id :scale/Z) -98}
                                    ::model/distance   {(t/id :scale/X) 0
                                                        (t/id :scale/Y) 0
                                                        (t/id :scale/Z) 0}
                                    ::model/reference? true}
                                   {::model/id         (t/id :p3)
                                    ::model/position   {(t/id :scale/X) 67
                                                        (t/id :scale/Y) 111
                                                        (t/id :scale/Z) 82}
                                    ::model/distance   {(t/id :scale/X) -129
                                                        (t/id :scale/Y) 212
                                                        (t/id :scale/Z) 180}}]
               ::model/spans [{::model/id (t/id :scale/X), ::model/display-name "X"}
                              {::model/id (t/id :scale/Y), ::model/display-name "Y"}
                              {::model/id (t/id :scale/Z), ::model/display-name "Z"}]}]
             (->> (d/q model/toplevel-loci-eids-q db)
               (map #(d/pull db model/toplevel-loci-pull %))
               model/add-distances))))))

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
