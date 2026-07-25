(ns cadro.model.scales-test
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

(defn- after-receives
  [& receives]
  (let [controller-id [::scales/hardware-address "00:00:01"]
        session       (-> session/base-session
                          (scales/insert-controllers [{::model/displays-as "HC-06"
                                                       ::scales/hardware-address "00:00:01"}])
                          (clara/fire-rules))
        session       (reduce (fn [session data]
                                (-> session
                                    (scales/add-received-data controller-id data)
                                    (clara/fire-rules)))
                              session
                              receives)
        eav-map       (:?eav-map (first (clara/query session pull/eav-map)))]
    (->> eav-map
         (keep (fn [[k v]]
                 (when (::scales/raw-count v)
                   k)))
         (map (fn [scale-id]
                (pull/pull session
                           [::model/id
                            ::model/displays-as
                            ::scales/raw-count
                            {::scales/controller
                             [::model/id]}]
                           scale-id))))))

(deftest t-add-received-data
  (let [scales (after-receives "X150;y250;Z350;T72;\0")]
    (is (= #{{::model/displays-as "X"
              ::scales/raw-count 150}
             {::model/displays-as "Y"
              ::scales/raw-count 250}
             {::model/displays-as "Z"
              ::scales/raw-count 350}
             {::model/displays-as "T"
              ::scales/raw-count 72}}
           (->> scales
                (map #(select-keys % [::model/displays-as ::scales/raw-count]))
                (into #{})))
        "It creates scales and stores raw values on receipt.")
    (is (every? (comp uuid? ::model/id) scales)
        "Every new scale is assigned a uuid.")
    (is (= 4 (count (map ::model/id scales)))
        "The new uuids are unique."))
  (let [scales (after-receives "X150;\0" "X152;\0")]
    (is (= #{{::model/displays-as "X"
              ::scales/raw-count 152}}
           (->> scales
                (map #(select-keys % [::model/displays-as ::scales/raw-count]))
                (into #{})))
        "It updates existing scale values."))
  (let [scales (after-receives "x150;y152;\u0000vTouchDRO_SIEG_1.3.1;x155;y157;\0")]
    (is (= #{{::model/displays-as "X"
              ::scales/raw-count 155}
             {::model/displays-as "Y"
              ::scales/raw-count 157}}
           (->> scales
                (map #(select-keys % [::model/displays-as ::scales/raw-count]))
                (into #{})))
        "It can parse and ignore version strings."))
  (testing "partial receives"
    (doseq [:let [full-data "X150;Y250;\u0000Z350;T72;\0"]
            i (range (count full-data))]
      (let [a      (subs full-data 0 i)
            b      (subs full-data i)
            scales (after-receives a b)]
        (is (= #{{::model/displays-as "X"
                  ::scales/raw-count 150}
                 {::model/displays-as "Y"
                  ::scales/raw-count 250}
                 {::model/displays-as "Z"
                  ::scales/raw-count 350}
                 {::model/displays-as "T"
                  ::scales/raw-count 72}}
               (->> scales
                    (map #(select-keys % [::model/displays-as ::scales/raw-count]))
                    (into #{})))
            (str "It correctly processes '" a "' then '" b "'."))))))

