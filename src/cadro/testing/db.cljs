(ns cadro.testing.db
  (:require
   [cadro.db :as db]
   [datascript.core :as d]))

(defn conn
  [& args]
  (let [conn (d/create-conn (db/schema))]
    (doseq [f args]
      (let [tx (f @conn)]
        (d/transact! conn tx)))
    conn))
