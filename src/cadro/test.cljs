(ns cadro.test
  (:require
   [cadro.db :as db]
   [clojure.walk :as w]
   [datascript.core :as d]))

(def keyword-db (atom {}))

(defn id
  [kw]
  {:pre [(keyword? kw)]}
  (swap! keyword-db (fn [db]
                      (if (contains? db kw)
                        db
                        (assoc db kw (d/squuid)))))
  (get @keyword-db kw))

(defn scenario
  "Run a test scenario with setup, actions, and assertions."
  [& args]
  (let [[title data & fs] (if (string? (first args))
                            args
                            (into ["Scenario"] args))
        conn (d/create-conn (db/schema))]
    (d/transact! conn data)
    (doseq [f fs]
      (if (vector? f)
        (let [[f-var & args] f
              result         (apply @f-var @conn args)]
          (d/transact! conn result))
        (f {:conn conn, :db @conn})))))
