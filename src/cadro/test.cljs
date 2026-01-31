(ns cadro.test
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [clara.rules :as clara]
   [clojure.test :refer [is]]
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
  ;; FIXME: Check for invariants/errors
  (let [session (reduce (fn [session [e a v]]
                          (clara/insert session (model/asserted e a v)))
                        session/base-session
                        datoms)]
    (clara/fire-rules session)))

(defn check [session & datoms]
  (let [session (clara/fire-rules session)
        eav-map (:?eav-map (first (clara/query session pull/eav-map)))]
    (doseq [[e a v] datoms
            :let [vs (get-in eav-map [e a])]
            :let [datom-msg (->> [e a v]
                                 (mapv readable-uuid)
                                 pr-str
                                 (str "checking datom "))]]
      (is (medley/find-first #(= % v)) datom-msg))))
