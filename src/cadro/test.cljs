(ns cadro.test
  (:require
   [clojure.walk :as w]
   [datascript.core :as d]))

(def keyword-db (atom {}))

(defn keyword->uuid
  [kw]
  {:pre [(keyword? kw)
         (= "uuid" (namespace kw))]}
  (swap! keyword-db (fn [db]
                      (if (contains? db kw)
                        db
                        (assoc db kw (d/squuid)))))
  (get @keyword-db kw))

(defn d
  "Massage test datoms before insertion."
  [x]
  (w/postwalk
   (fn [x]
     (if (and (keyword? x) (= "uuid" (namespace x)))
       (keyword->uuid x)
       x))
   x))

