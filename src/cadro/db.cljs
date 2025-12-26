(ns cadro.db
  (:require
   [datascript.core :as d]
   [datascript.conn :as dconn]
   [re-posh.core :as re-posh]))

(defonce ^:dynamic *conn* nil)
(def ^:private schema-atom (atom {}))

(defn schema
  "Retrieves all registered schema."
  []
  @schema-atom)

(defn register-schema!
  "Registers some partial DataScript schema for model attributes."
  [subschema]
  (swap! schema-atom merge subschema)
  (when *conn*
    (dconn/reset-schema! *conn* @schema-atom))
  nil)

(defn connect!
  "Creates a database connection with all registered schema.

  Tries to reuse an existing connection for database stability when running
  in debug mode."
  []
  (when-not *conn*
    (let [conn (d/create-conn (schema))]
      (re-posh/connect! conn)
      (set! *conn* conn)))
  *conn*)

(def squuid d/squuid)
