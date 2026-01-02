(ns ^:dev/always cadro.user
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.model.scale-controller :as scale-controller]
   [cadro.test :as t]
   [clojure.spec.alpha :as s]
   [cljs.repl :refer [doc apropos source]]
   [datascript.core :as d]))

(defn q
  "Query the app database."
  [expr & args]
  (apply d/q expr @db/*conn* args))

(defn transact
  "Transact data to the db."
  [& args]
  @(apply d/transact db/*conn* args))

(defn entity
  "Retrieve and entity."
  [eid]
  (d/touch (d/entity @db/*conn* eid)))

(defn pull
  [selector eid]
  (d/pull @db/*conn* selector eid))
