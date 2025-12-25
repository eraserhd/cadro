(ns cadro.db
  (:require
   [datascript.core :as d]
   [re-posh.core :as re-posh]))

(def ^:private schema-atom (atom {}))

(defn register-schema! [subschema]
  (swap! schema-atom merge subschema))

(defn schema
  "Retrieves all registered schema."
  []
  @schema-atom)

(def ^:dynamic *conn* nil)

(defn connect!
  "Creates a database connection with all registered schema."
  []
  (let [conn (d/create-conn (schema))]
    (re-posh/connect! conn)
    (set! *conn* conn)
    conn))
