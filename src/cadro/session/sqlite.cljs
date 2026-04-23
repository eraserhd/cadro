(ns cadro.session.sqlite
  "SQLite storage operations for session data."
  (:require
   [cadro.session.storage :as storage]
   ["@capacitor-community/sqlite" :refer [CapacitorSQLite]]))

(def ^:private db-name "cadro.db")

(defn- init-db!
  "Initialize the SQLite database and create table if needed."
  []
  (-> CapacitorSQLite
      (.createConnection (clj->js {:database db-name
                                   :version 1
                                   :encrypted false
                                   :mode "no-encryption"}))
      (.then (fn []
               (.open CapacitorSQLite (clj->js {:database db-name}))))
      (.then (fn []
               (.execute CapacitorSQLite (clj->js {:database db-name
                                                   :statements "CREATE TABLE IF NOT EXISTS sessions (id INTEGER PRIMARY KEY, session TEXT);"}))))
      (.then (fn []
               db-name))
      (.catch (fn [err]
                (js/console.error "Failed to initialize database:" err)
                (throw err)))))

(defn- ensure-db!
  "Ensure database is initialized and return connection."
  [db-atom]
  (if-let [db @db-atom]
    (do
      (js/console.log "SQLite: using existing connection")
      (js/Promise.resolve db))
    (do
      (js/console.log "SQLite: initializing new connection")
      (-> (init-db!)
          (.then (fn [db]
                   (js/console.log "SQLite: connection initialized, storing in atom")
                   (reset! db-atom db)
                   db))))))

(defrecord SQLiteBackend [db]
  storage/SessionStorage
  (save! [this data]
    (-> (ensure-db! db)
        (.then (fn [db-name]
                 (.executeSet CapacitorSQLite (clj->js {:database db-name
                                                        :set [{:statement "INSERT OR REPLACE INTO sessions (id, session) VALUES (1, ?)"
                                                               :values [data]}]}))))
        (.catch (fn [err]
                  (js/console.error "Failed to save session:" err)
                  (throw err)))))

  (load! [this]
    (-> (ensure-db! db)
        (.then (fn [db-name]
                 (.query CapacitorSQLite (clj->js {:database db-name
                                                   :statement "SELECT session FROM sessions WHERE id = 1"
                                                   :values []}))))
        (.then (fn [result]
                 (if-let [rows (.-values result)]
                   (when (pos? (.-length rows))
                     (.-session (aget rows 0)))
                   nil)))
        (.catch (fn [err]
                  (js/console.error "Failed to load session:" err)
                  nil)))))

(defn create []
  (->SQLiteBackend (atom nil)))
