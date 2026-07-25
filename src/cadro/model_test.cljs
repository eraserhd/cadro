(ns cadro.model-test
  (:require-macros
   [cadro.test-macros :as t])
  (:require
   [cadro.model :as model]
   [cadro.model.scales :as scales]
   [cadro.session :as session]
   [cadro.test :as t]
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clojure.test :refer [deftest testing is]]
   [net.eraserhead.clara-eql.pull :as pull]))

(deftest t-set-reference
  (t/scenario "can retrieve the current reference"
    [(t/id :ref) ::model/coordinates {}]
    [(t/id :ref) ::model/display-order 0]
    (model/set-reference (t/id :ref))
    (t/has-no-errors)
    (model/reference) => (t/id :ref))
  (t/scenario "can update the current reference"
    [(t/id :p1) ::model/coordinates {}]
    [(t/id :p1) ::model/display-order 0]
    [(t/id :p2) ::model/coordinates {}]
    [(t/id :p2) ::model/display-order 0]
    (model/set-reference (t/id :p1))
    (model/set-reference (t/id :p2))
    (t/has-no-errors)
    (model/reference) => (t/id :p2))
  (t/scenario "cannot have more than one reference point in session"
    [(t/id :p1) ::model/coordinates {}]
    [(t/id :p1) ::model/display-order 0]
    [(t/id :p2) ::model/coordinates {}]
    [(t/id :p2) ::model/display-order 0]
    [(t/id :p1) ::model/reference? true]
    [(t/id :p2) ::model/reference? true]
    (t/has-error (model/->InvariantError "more than one reference point in session" {:count 2})))
  (t/scenario "the reference point must have coordinates"
    (model/set-reference (t/id :id))
    (t/has-error (model/->InvariantError "reference point does not have coordinates" {:id (t/id :id)}))))

(defn- controllers [session]
  (->> (clara/query session scales/controllers)
       (sort-by :?hardware-address)
       (map #(dissoc % :?id))))

(deftest t-insert-controllers
  (t/scenario "inserting controllers"
    (scales/insert-controllers [{::model/displays-as      "Nexus 7"
                                 ::scales/hardware-address "00:00:01"}
                                {::model/displays-as      "HC-06"
                                 ::scales/hardware-address "02:03:04"}])
    (t/has-no-errors)
    controllers => [{:?displays-as       "Nexus 7"
                     :?hardware-address  "00:00:01"
                     :?connection-status :disconnected}
                    {:?displays-as       "HC-06"
                     :?hardware-address  "02:03:04"
                     :?connection-status :disconnected}]
    (scales/insert-controllers [{::model/displays-as      "Nexus 7 Renamed"
                                 ::scales/hardware-address "00:00:01"}])
    (t/has-no-errors)
    controllers => [{:?displays-as       "Nexus 7 Renamed"
                     :?hardware-address  "00:00:01"
                     :?connection-status :disconnected}
                    {:?displays-as       "HC-06"
                     :?hardware-address  "02:03:04"
                     :?connection-status :disconnected}]))

(deftest t-new-fixture
  (let [new-fixture (comp :session model/new-fixture)]
    (t/scenario "creating a new fixture"
      (new-fixture {:fixture-id (t/id :f)
                    :point-id   (t/id :p)})
      (t/has-no-errors)
      (t/has-datoms [(t/id :f) ::model/displays-as "New Machine"]
                    [(t/id :f) ::model/transforms (t/id :p)]
                    [(t/id :p) ::model/displays-as "Origin"]
                    [(t/id :p) ::model/display-order 0]
                    [(t/id :p) ::model/coordinates {}]
                    [(t/id :p) ::model/reference? true]))))

(deftest t-store-scale-to-reference
  (t/scenario "storing scale to reference"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::scales/raw-count 42]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/transform {::tr/scale {"X" 0.5}}]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/coordinates {"X" 78}]
    [(t/id :p) ::model/reference? true]
    [(t/id :p) ::model/display-order 0]
    (model/store-scale-to-reference (t/id :x))
    (t/has-no-errors)
    (t/has-datoms [(t/id :p) ::model/coordinates {"X" (/ 42 2)}]
                  ;; The axis should always appear zero after storing
                  [(t/id :x) ::model/transformed-count 0])))

(deftest t-drop-pin
  (t/scenario "dropping a pin"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::scales/raw-count 42]
    [(t/id :y) ::model/displays-as "Y"]
    [(t/id :y) ::scales/raw-count 111]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/spans (t/id :y)]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :m) ::model/transform {::tr/scale {"X" 0.30, "Y" 0.20}}]
    [(t/id :p) ::model/display-order 0]
    [(t/id :p) ::model/coordinates {"X" 78, "Y" 96}]
    [(t/id :p) ::model/reference? true]
    (model/drop-pin (t/id :pin))
    (t/has-no-errors)
    (t/has-datoms [(t/id :m) ::model/transforms (t/id :p)]
                  [(t/id :m) ::model/transforms (t/id :pin)]
                  [(t/id :pin) ::model/displays-as "A"]
                  [(t/id :pin) ::model/display-order 1]
                  [(t/id :pin) ::model/coordinates {"X" (* 42 0.30), "Y" (* 111 0.2)}]
                  [(t/id :pin) ::model/reference? true])))

(deftest t-axes-display
  (t/scenario "with no scale factor"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::scales/raw-count 428]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/displays-as "Mill"]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/display-order 0]
    [(t/id :p) ::model/coordinates {"X" 42}]
    [(t/id :p) ::model/reference? true]
    (t/has-no-errors)
    (model/axes) => [{::model/id (t/id :x)
                      ::model/displays-as "X"
                      ::model/transformed-count (- 428 42)}])
  (t/scenario "when a transform with a scale factor of 1/2 is present"
    [(t/id :x) ::model/displays-as "X"]
    [(t/id :x) ::scales/raw-count 428]
    [(t/id :m) ::model/spans (t/id :x)]
    [(t/id :m) ::model/displays-as "Mill"]
    [(t/id :m) ::model/transform {::tr/scale {"X" 0.5}}]
    [(t/id :m) ::model/transforms (t/id :p)]
    [(t/id :p) ::model/display-order 0]
    [(t/id :p) ::model/coordinates {"X" 42}]
    [(t/id :p) ::model/reference? true]
    (t/has-no-errors)
    (model/axes) => [{::model/id (t/id :x)
                      ::model/displays-as "X"
                      ::model/transformed-count (- (/ 428 2) 42)}]))

(deftest t-computed-distances
  (t/scenario "without fixture transformation"
    [(t/id :m)   ::model/transforms  (t/id :ref)]
    [(t/id :m)   ::model/transforms  (t/id :p1)]
    [(t/id :ref) ::model/coordinates {"X" 42}]
    [(t/id :ref) ::model/reference?  true]
    [(t/id :p1)  ::model/coordinates {"X" 179}]
    (t/has-datoms [(t/id :ref) ::model/distance {"X" 0}]
                  [(t/id :p1)  ::model/distance {"X" (- 179 42)}]))
  (t/scenario "with fixture scaling, both in the same fixture"
    [(t/id :m)   ::model/transforms  (t/id :ref)]
    [(t/id :m)   ::model/transforms  (t/id :p1)]
    [(t/id :m)   ::model/transform   {::tr/scale {"X" 0.5}}]
    [(t/id :ref) ::model/coordinates {"X" 42}]
    [(t/id :ref) ::model/reference?  true]
    [(t/id :p1)  ::model/coordinates {"X" 179}]
    (t/has-datoms [(t/id :ref) ::model/distance {"X" 0}]
                  [(t/id :p1)  ::model/distance {"X" (- 179 42)}]))
  (t/scenario "with fixture scaling, in different fixtures"
    [(t/id :m1)  ::model/transforms  (t/id :ref)]
    [(t/id :m1)  ::model/transform   {::tr/scale {"X" 0.5}}]
    [(t/id :m2)  ::model/transforms  (t/id :p1)]
    [(t/id :ref) ::model/coordinates {"X" 42}]
    [(t/id :ref) ::model/reference?  true]
    [(t/id :p1)  ::model/coordinates {"X" 179}]
    (t/has-datoms [(t/id :ref) ::model/distance {"X" 0}]
                  [(t/id :p1)  ::model/distance {"X" (- 179 (* 2 42))}])))
