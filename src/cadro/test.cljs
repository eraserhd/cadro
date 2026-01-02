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
  "Run a test scenario with setup, actions, and assertions."
  [& args]
  (let [[title data & fs] (if (string? (first args))
                            args
                            (into ["Scenario"] args))
        conn (d/create-conn (db/schema))]
    (d/transact! conn (d data))
    (doseq [f fs]
      (if (vector? f)
        (let [[f-var & args] (d f)
              result         (apply @f-var @conn args)]
          (d/transact! conn result))
        (f (merge @keyword-db {:conn conn, :db @conn}))))))
