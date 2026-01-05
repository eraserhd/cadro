(ns cadro.transforms
  (:refer-clojure :exclude [-]))

(defn- pairwise
  [f default a b]
  (->> (concat (keys a) (keys b))
       distinct
       (map (fn [k]
              [k (f (get a k default) (get b k default))]))
       (into {})))

(def - (partial pairwise clojure.core/- 0))
