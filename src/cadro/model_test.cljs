(ns cadro.model-test
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [clara.rules :as clara]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]
   [net.eraserhead.clara-eql.pull :as pull]))

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
