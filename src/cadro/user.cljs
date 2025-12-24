(ns ^:dev/always cadro.user
  (:require
   [cadro.db :as db]
   [cljs.repl :refer [doc apropos source]]
   [datascript.core :as d]))

(defn q
  "Query the app database."
  [expr & args]
  (apply d/q expr @db/*conn* args))

(defn transact
  "Transact data to the db."
  [& args]
  (apply d/transact db/*conn* args))
