(ns cadro.transforms
  (:refer-clojure :exclude [-])
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::locus (s/or :nil nil? :non-nil (s/map-of any? number?)))

(defn- pairwise
  [f default a b]
  {:pre [(s/valid? ::locus a)
         (s/valid? ::locus b)]}
  (->> (concat (keys a) (keys b))
       distinct
       (map (fn [k]
              [k (f (get a k default) (get b k default))]))
       (into {})))

(def - (partial pairwise clojure.core/- 0))

(defn inverse [m]
  (if-let [scale (::scale m)]
    {::scale (reduce-kv (fn [m k v]
                          (assoc m k (/ v)))
                        {}
                        scale)}
    {}))

(defn transform [p m]
  (if-let [scale (::scale m)]
    (pairwise * 1.0 p scale)
    p))
