(ns cadro.test
  (:require
   [cadro.db :as db]
   [clojure.test :refer [testing]]
   [clojure.walk :as w]
   [datascript.core :as d]))

(let [ids (atom {})]
  (defn id
    "Reproducibly maps a keyword to a UUID, for test readability."
    [kw]
    {:pre [(keyword? kw)]}
    (swap! ids (fn [db]
                 (if (contains? db kw)
                   db
                   (assoc db kw (d/squuid)))))
    (get @ids kw)))

(defn scenario
  "Run a test scenario with setup, actions, and assertions."
  [& args]
  (let [[title data & fs] (if (string? (first args))
                            args
                            (into ["Scenario"] args))]
    (testing title
      (let [conn (d/create-conn (db/schema))]
        (d/transact! conn data)
        (doseq [f fs]
          (if (vector? f)
            (let [[f-var & args] f
                  result         (apply @f-var @conn args)]
              (d/transact! conn result))
            (f {:conn conn, :db @conn})))))))
