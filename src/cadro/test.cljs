(ns cadro.test
  (:require
   [cadro.db :as db]
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
  "Massage test data before insertion."
  [x]
  (w/postwalk
   (fn [x]
     (if (and (keyword? x) (= "uuid" (namespace x)))
       (keyword->uuid x)
       x))
   x))

(defn scenario
  [data & fs]
  (let [conn (d/create-conn (db/schema))]
    (d/transact! conn (d data))
    (doseq [f fs]
      (if (vector? f)
        (let [[f-var & args] (d f)
              _              (prn :f f-var @conn args)
              result         (apply @f-var @conn args)]
          (d/transact! conn result))
        (do
          (prn :not-vector f)
          (f {:conn conn, :db @conn}))))))
