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

(defmulti invariants
  #(throw (ex-info "invariants is not intended to be called directly." {})))

(defn failed-invariants
  [db]
  (->> (methods invariants)
       vals
       (mapcat #(% db))))

(defn check-invariants
  [{db :db-after}]
  (when-let [failures (seq (failed-invariants db))]
    (js/console.group "FAILED DATABASE INVARIANTS:")
    (doseq [failure failures]
      (js/console.error "%s" (pr-str failure)))
    (js/console.groupCollapsed "Datoms")
    (doseq [d (d/datoms db :eavt)]
      (js/console.log "%s" (pr-str d)))
    (js/console.groupEnd)
    (js/console.groupEnd)
    (when ^boolean goog.DEBUG
      (throw (ex-info "Database fails invariant checks" {:db db, :failures failures})))))

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
      (d/listen! conn :check-invariants check-invariants)
      (d/listen! conn :persist-to-localStorage persist-to-localStorage)
      (set! *conn* conn)))
  *conn*)

(def squuid d/squuid)
