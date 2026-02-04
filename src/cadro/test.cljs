(ns cadro.test
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [clara.rules :as clara]
   [clojure.test :refer [testing is]]
   [medley.core :as medley]
   [net.eraserhead.clara-eql.pull :as pull]))

(def ids (atom {}))
(defn id [k]
  (swap! ids (fn [m]
               (cond-> m
                 (not (contains? m k)) (assoc k (random-uuid)))))
  (get @ids k))

(defn readable-uuid [x]
  (if (uuid? x)
    (if-let [[[k]] (seq (filter (fn [[k v]] (= v x)) @ids))]
      `(~'t/id ~k)
      x)
    x))

(defn session [datoms]
  (-> (reduce (fn [session [e a v]]
                (clara/insert session (model/asserted e a v)))
              session/base-session
              datoms)
      (clara/fire-rules)))

(defn check [session f checker]
  (let [session (clara/fire-rules session)
        actual  (f session)]
    (if (fn? checker)
      (checker actual)
      (is (= actual checker)))
    session))

(defn has-datoms [session & datoms]
  (let [session (clara/fire-rules session)
        eav-map (:?eav-map (first (clara/query session pull/eav-map)))]
    (doseq [[e a v] datoms
            :let [vs (get-in eav-map [e a])
                  datom-msg (->> [e a v]
                                 (mapv readable-uuid)
                                 pr-str
                                 (str "checking datom "))]]
      (is (medley/find-first #(= % v) vs) datom-msg))))

(defn has-no-errors [session]
  (let [session (clara/fire-rules session)]
    (is (empty? (model/errors session)) "has unexpected errors")
    session))

(defn has-error [session error]
  (let [session (clara/fire-rules session)]
    (is (= [error] (model/errors session))
        (str "did not find expected error " (pr-str error)))
    session))

(defn dump-session [session]
  (let [eav-map (:?eav-map (first (clara/query session pull/eav-map)))]
    (doseq [[e a->v] eav-map
            [a v] a->v]
      (prn e a v))
    session))
