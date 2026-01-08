(ns cadro.db
  (:require
   [datascript.core :as d]
   [datascript.conn :as dconn]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   [re-posh.db :as re-posh.db]))

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

(defn- persist-to-localStorage
  [{:keys [db-after]}]
  (.setItem js/localStorage "db" (js/JSON.stringify (d/serializable db-after))))

(defn- load-from-localStorage
  "Rehydrates DataScript db from localStorage.

  Returns nil if it cannot."
  []
  (when-let [conn (some-> js/localStorage
                    (.getItem "db")
                    js/JSON.parse
                    d/from-serializable
                    d/conn-from-db)]
    (d/reset-schema! conn (schema))
    conn))

(defn connect!
  "Creates a database connection with all registered schema.

  Tries to reuse an existing connection for database stability when running
  in debug mode."
  []
  (when-not *conn*
    (let [conn (or (load-from-localStorage)
                   (d/create-conn (schema)))]
      (re-posh/connect! conn)
      (d/listen! conn :persist-to-localStorage persist-to-localStorage)
      (set! *conn* conn)))
  *conn*)

(def squuid d/squuid)
