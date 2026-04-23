(ns cadro.session.sqlite
  "SQLite storage operations for session data."
  (:require
   [cadro.session.storage :as storage]
   ["@capacitor-community/sqlite" :refer [CapacitorSQLite]]))

(def ^:private db-name "cadro.db")

(defonce ^:private db-connection (atom nil))

(defn- init-db!
  "Initialize the SQLite database and create table if needed."
  []
  (-> CapacitorSQLite
      (.createConnection (clj->js {:database db-name
                                   :version 1
                                   :encrypted false
                                   :mode "no-encryption"}))
      (.then (fn [db]
               (-> (.open db)
                   (.then (fn []
                            (.execute db (clj->js {:statements
                                                   ["CREATE TABLE IF NOT EXISTS sessions (id INTEGER PRIMARY KEY, session TEXT)"]}))))
                   (.then (fn [] db)))))
      (.catch (fn [err]
                (js/console.error "Failed to initialize database:" err)
                (throw err)))))

(defn ensure-db!
  "Ensure database is initialized and return connection."
  []
  (if-let [db @db-connection]
    (js/Promise.resolve db)
    (-> (init-db!)
        (.then (fn [db]
                 (reset! db-connection db)
                 db)))))

(defrecord SQLiteBackend []
  storage/SessionStorage
  (save! [this data]
    (-> (ensure-db!)
        (.then (fn [db]
                 (.executeSet db (clj->js {:statements
                                           [{:statement "INSERT OR REPLACE INTO sessions (id, session) VALUES (1, ?)"
                                             :values [data]}]}))))
        (.catch (fn [err]
                  (js/console.error "Failed to save session:" err)
                  (throw err)))))

  (load! [this]
    (-> (ensure-db!)
        (.then (fn [db]
                 (.query db (clj->js {:statement "SELECT session FROM sessions WHERE id = 1"
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
  (->SQLiteBackend))
