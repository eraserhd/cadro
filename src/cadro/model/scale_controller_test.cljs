(ns cadro.model.scale-controller-test
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
   [cadro.model.scale-controller :as scale-controller]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]))

(deftest t-add-controllers-tx
  (let [conn (d/create-conn (db/schema))
        tx   (scale-controller/add-controllers-tx @conn [{::object/display-name      "Nexus 7"
                                                          ::scale-controller/address "00:00:01"}
                                                         {::object/display-name      "HC-06"
                                                          ::scale-controller/address "02:03:04"}])
        _    (d/transact! conn tx)
        pull [::object/id
              ::object/display-name
              ::scale-controller/address
              ::scale-controller/status]
        c1   (d/pull @conn pull [::scale-controller/address "00:00:01"])
        c2   (d/pull @conn pull [::scale-controller/address "02:03:04"])
        tx2  (scale-controller/add-controllers-tx @conn [{::object/display-name       "Nexus 7 Renamed"
                                                          ::scale-controller/address  "00:00:01"}])
        _    (d/transact! conn tx2)
        c1'  (d/pull @conn pull [::scale-controller/address "00:00:01"])]
    (is (= {::object/display-name "Nexus 7"
            ::scale-controller/address "00:00:01"
            ::scale-controller/status :disconnected}
           (dissoc c1 ::object/id))
        "It stores the 'Nexus 7' controller, marking as disconnected.")
    (is (= {::object/display-name "HC-06"
            ::scale-controller/address "02:03:04"
            ::scale-controller/status :disconnected}
           (dissoc c2 ::object/id))
        "It stores the 'HC-06' controller, marking as disconnected.")
    (is (uuid? (::object/id c1))
        "It creates a UUID for 'Nexus 7'.")
    (is (uuid? (::object/id c2))
        "It creates a UUID for 'HC-06'.")
    (is (= "Nexus 7 Renamed" (::object/display-name c1'))
        "It updates a name when a new one is received.")
    (is (= (::object/id c1) (::object/id c1'))
        "It does not update a UUID.")))

(defn- after-receives
  [& receives]
  (let [controller-id [::scale-controller/address "00:00:01"]
        conn          (d/create-conn (db/schema))
        tx            (scale-controller/add-controllers-tx @conn [{::object/display-name "HC-06"
                                                                   ::scale-controller/address "00:00:01"}])
        _             (d/transact! conn tx)
        _             (doseq [data receives]
                        (let [tx (scale-controller/add-received-data-tx @conn controller-id data)]
                          (d/transact! conn tx)))]
    (d/entity @conn controller-id)))

(deftest t-add-received-data-tx
  (let [controller (after-receives "X150;Y250;Z350;T72;\n")]
    (is (= #{{::object/display-name "X"
              ::scale/raw-value 150}
             {::object/display-name "Y"
              ::scale/raw-value 250}
             {::object/display-name "Z"
              ::scale/raw-value 350}
             {::object/display-name "T"
              ::scale/raw-value 72}}
           (->> (::scale/_controller controller)
                (map #(select-keys % [::object/display-name ::scale/raw-value]))
                (into #{})))
        "It creates scales and stores raw values on receipt.")
    (is (every? (comp uuid? ::object/id) (::scale/_controller controller))
        "Every new scale is assigned a uuid.")
    (is (= 4 (count (map ::object/id (::scale/_controller controller))))
        "The new uuids are unique."))
  (let [controller (after-receives "X150;\n" "X152;\n")]
    (is (= #{{::object/display-name "X"
              ::scale/raw-value 152}}
           (->> (::scale/_controller controller)
                (map #(select-keys % [::object/display-name ::scale/raw-value]))
                (into #{})))
        "It updates existing scale values."))
  (testing "partial receives"
    (doseq [:let [full-data "X150;Y250;Z350;T72;\n"]
            i (range (count full-data))]
      (let [a          (subs full-data 0 i)
            b          (subs full-data i)
            controller (after-receives a b)]
        (is (= #{{::object/display-name "X"
                  ::scale/raw-value 150}
                 {::object/display-name "Y"
                  ::scale/raw-value 250}
                 {::object/display-name "Z"
                  ::scale/raw-value 350}
                 {::object/display-name "T"
                  ::scale/raw-value 72}}
               (->> (::scale/_controller controller)
                    (map #(select-keys % [::object/display-name ::scale/raw-value]))
                    (into #{})))
            (str "It correctly processes '" a "' then '" b "'."))))))
