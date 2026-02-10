(ns cadro.model-test
  (:require-macros
   [cadro.test-macros :as t])
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [cadro.test :as t]
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clojure.test :refer [deftest testing is]]
   [net.eraserhead.clara-eql.pull :as pull]))

(deftest t-set-reference
  (t/scenario "can retrieve the current reference"
    [(t/id :ref) ::model/coordinates {}]
    (model/set-reference (t/id :ref))
    (t/has-no-errors)
    (model/reference) => (t/id :ref))
  (t/scenario "can update the current reference"
    [(t/id :p1) ::model/coordinates {}]
    [(t/id :p2) ::model/coordinates {}]
    (model/set-reference (t/id :p1))
    (model/set-reference (t/id :p2))
    (t/has-no-errors)
    (model/reference) => (t/id :p2))
  (t/scenario "cannot have more than one reference point in session"
    [(t/id :p1) ::model/coordinates {}]
    [(t/id :p2) ::model/coordinates {}]
    [(t/id :p1) ::model/reference? true]
    [(t/id :p2) ::model/reference? true]
    (t/has-error (model/->InvariantError "more than one reference point in session" {:count 2})))
  (t/scenario "the reference point must have coordinates"
    (model/set-reference (t/id :id))
    (t/has-error (model/->InvariantError "reference point does not have coordinates" {:id (t/id :id)}))))

(defn- controllers [session]
  (->> (clara/query session model/controllers)
       (sort-by :?hardware-address)
       (map #(dissoc % :?id))))

(deftest t-insert-controllers
  (t/scenario "inserting controllers"
    (model/insert-controllers [{::model/displays-as      "Nexus 7"
                                ::model/hardware-address "00:00:01"}
                               {::model/displays-as      "HC-06"
                                ::model/hardware-address "02:03:04"}])
    controllers => [{:?displays-as       "Nexus 7"
                     :?hardware-address  "00:00:01"
                     :?connection-status :disconnected}
                    {:?displays-as       "HC-06"
                     :?hardware-address  "02:03:04"
                     :?connection-status :disconnected}]
    (model/insert-controllers [{::model/displays-as      "Nexus 7 Renamed"
                                ::model/hardware-address "00:00:01"}])
    controllers => [{:?displays-as       "Nexus 7 Renamed"
                     :?hardware-address  "00:00:01"
                     :?connection-status :disconnected}
                    {:?displays-as       "HC-06"
                     :?hardware-address  "02:03:04"
                     :?connection-status :disconnected}]))

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
  (t/scenario "storing scale to reference"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::model/raw-count 42]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/transform {::tr/scale {"X" 0.5}}]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/coordinates {"X" 78}]
    [(t/id :p) ::model/reference? true]
    (model/store-scale-to-reference (t/id :x))
    (t/has-datoms [(t/id :p) ::model/coordinates {"X" 21}]
                  ;; The axis should always appear zero after storing
                  [(t/id :x) ::model/transformed-count 0])))

(deftest t-drop-pin
  (t/scenario "dropping a pin"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::model/raw-count 42]
    [(t/id :y) ::model/displays-as "Y"]
    [(t/id :y) ::model/raw-count 111]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/spans (t/id :y)]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/coordinates {"X" 78, "Y" 96}]
    [(t/id :p) ::model/reference? true]
    (model/drop-pin (t/id :pin))
    (t/has-datoms [(t/id :m) ::model/transforms (t/id :p)]
                  [(t/id :m) ::model/transforms (t/id :pin)]
                  [(t/id :pin) ::model/displays-as "A"]
                  [(t/id :pin) ::model/coordinates {"X" 42, "Y" 111}])))

(deftest t-axes-display
  (t/scenario "with no scale factor"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::model/raw-count 428]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/displays-as "Mill"]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/coordinates {"X" 42}]
    [(t/id :p) ::model/reference? true]
    (model/axes) => [{::model/id (t/id :x)
                      ::model/displays-as "X"
                      ::model/transformed-count (- 428 42)}])
  (t/scenario "when a transform with a scale factor of 1/2 is present"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::model/raw-count 428]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/displays-as "Mill"]
    [(t/id :m) ::model/transform {::tr/scale {"X" 0.5}}]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/coordinates {"X" 42}]
    [(t/id :p) ::model/reference? true]
    (model/axes) => [{::model/id (t/id :x)
                      ::model/displays-as "X"
                      ::model/transformed-count (- (/ 428 2) 42)}]))

