(ns cadro.testing.db
  (:require
   [cadro.db :as db]
   [datascript.core :as d]))

(defn conn
  [& args]
  (d/create-conn (db/schema)))
