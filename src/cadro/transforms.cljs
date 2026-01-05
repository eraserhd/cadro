(ns cadro.transforms
  (:refer-clojure :exclude [-]))

(defn- pairwise
  [f a b default]
  (->> (concat (keys a) (keys b))
       distinct
       (map (fn [k]
              [k (f (get a k default) (get b k default))]))
       (into {})))

(defn -
  [a b]
  (pairwise clojure.core/- a b 0))
