(ns cadro.model-test
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [cadro.test :as t]
   [clara.rules :as clara]
   [clojure.test :refer [deftest testing is]]
   [net.eraserhead.clara-eql.pull :as pull]))

(deftest t-set-reference
  (let [session (-> session/base-session
                    (clara/insert (model/asserted (t/id :ref) ::model/coordinates {}))
                    (model/set-reference (t/id :ref))
                    (clara/fire-rules))]
    (is (= (t/id :ref) (model/reference session))
        "Can retrieve current reference.")
    (is (empty? (model/errors session))
        "No invariant errors"))
  (let [session (-> session/base-session
                    (clara/insert (model/asserted (t/id :p1) ::model/coordinates {}))
                    (clara/insert (model/asserted (t/id :p2) ::model/coordinates {}))
                    (model/set-reference (t/id :p1))
                    (model/set-reference (t/id :p2))
                    (clara/fire-rules))]
    (is (= (t/id :p2) (model/reference session))
        "Updates current reference.")
    (is (empty? (model/errors session))
        "No invariant errors"))
  (let [session (-> session/base-session
                    (clara/insert (model/asserted (t/id :p1) ::model/coordinates {}))
                    (clara/insert (model/asserted (t/id :p2) ::model/coordinates {}))
                    (clara/insert (model/asserted (t/id :p1) ::model/reference? true))
                    (clara/insert (model/asserted (t/id :p2) ::model/reference? true))
                    (clara/fire-rules))]
    (is (= [(model/->InvariantError "more than one reference point in session" {:count 2})]
           (model/errors session))))
  (let [session (-> session/base-session
                    (model/set-reference (t/id :id))
                    (clara/fire-rules))]
    (is (= [(model/->InvariantError "reference point does not have coordinates" {:id (t/id :id)})]
           (model/errors session)))))

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
        session       (-> session/base-session
                          (model/insert-controllers [{::model/displays-as "HC-06"
                                                      ::model/hardware-address "00:00:01"}])
                          (clara/fire-rules))
        session       (reduce (fn [session data]
                                (-> session
                                    (model/add-received-data controller-id data)
                                    (clara/fire-rules)))
                              session
                              receives)
        eav-map       (:?eav-map (first (clara/query session pull/eav-map)))]
    (->> eav-map
         (keep (fn [[k v]]
                 (when (::model/raw-count v)
                   k)))
         (map (fn [scale-id]
                (pull/pull session
                           [::model/id
                            ::model/displays-as
                            ::model/raw-count
                            {::model/controller
                             [::model/id]}]
                           scale-id))))))

(deftest t-add-received-data
  (let [scales (after-receives "X150;Y250;Z350;T72;\n")]
    (is (= #{{::model/displays-as "X"
              ::model/raw-count 150}
             {::model/displays-as "Y"
              ::model/raw-count 250}
             {::model/displays-as "Z"
              ::model/raw-count 350}
             {::model/displays-as "T"
              ::model/raw-count 72}}
           (->> scales
                (map #(select-keys % [::model/displays-as ::model/raw-count]))
                (into #{})))
        "It creates scales and stores raw values on receipt.")
    (is (every? (comp uuid? ::model/id) scales)
        "Every new scale is assigned a uuid.")
    (is (= 4 (count (map ::model/id scales)))
        "The new uuids are unique."))
  (let [scales (after-receives "X150;\n" "X152;\n")]
    (is (= #{{::model/displays-as "X"
              ::model/raw-count 152}}
           (->> scales
                (map #(select-keys % [::model/displays-as ::model/raw-count]))
                (into #{})))
        "It updates existing scale values."))
  (testing "partial receives"
    (doseq [:let [full-data "X150;Y250;Z350;T72;\n"]
            i (range (count full-data))]
      (let [a      (subs full-data 0 i)
            b      (subs full-data i)
            scales (after-receives a b)]
        (is (= #{{::model/displays-as "X"
                  ::model/raw-count 150}
                 {::model/displays-as "Y"
                  ::model/raw-count 250}
                 {::model/displays-as "Z"
                  ::model/raw-count 350}
                 {::model/displays-as "T"
                  ::model/raw-count 72}}
               (->> scales
                    (map #(select-keys % [::model/displays-as ::model/raw-count]))
                    (into #{})))
            (str "It correctly processes '" a "' then '" b "'."))))))

(deftest t-store-scale-to-reference
  (-> (t/session [[(t/id :x) ::model/displays-as "X"]
                  [(t/id :x) ::model/raw-count 42]
                  [(t/id :m) ::model/spans (t/id :x)]
                  [(t/id :m) ::model/transforms (t/id :p)]
                  [(t/id :p) ::model/coordinates {"X" 78}]
                  [(t/id :p) ::model/reference? true]])
      (model/store-scale-to-reference (t/id :x))
      (t/check [(t/id :p) ::model/coordinates {"X" 42}])))

(deftest t-drop-pin
  (-> (t/session [[(t/id :x) ::model/displays-as "X"]
                  [(t/id :x) ::model/raw-count 42]
                  [(t/id :y) ::model/displays-as "Y"]
                  [(t/id :y) ::model/raw-count 111]
                  [(t/id :m) ::model/spans (t/id :x)]
                  [(t/id :m) ::model/spans (t/id :y)]
                  [(t/id :m) ::model/transforms (t/id :p)]
                  [(t/id :p) ::model/coordinates {"X" 78, "Y" 96}]
                  [(t/id :p) ::model/reference? true]])
      (model/drop-pin (t/id :pin))
      (t/check [(t/id :m) ::model/transforms (t/id :p)]
               [(t/id :m) ::model/transforms (t/id :pin)]
               [(t/id :pin) ::model/displays-as "A"]
               [(t/id :pin) ::model/coordinates {"X" 42, "Y" 111}])))
