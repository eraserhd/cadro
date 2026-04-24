(ns cadro.session.sqlite
  "SQLite storage operations for session data."
  (:require
   [cadro.session.storage :as storage]
   ["@capacitor-community/sqlite" :refer [CapacitorSQLite]]))

(def ^:private db-name "cadro.db")

(defrecord SQLiteBackend []
  storage/SessionStorage
  (load! [this]
    (-> (.query CapacitorSQLite (clj->js {:database db-name
                                          :statement "SELECT session FROM sessions WHERE id = 1"
                                          :values []}))
        (.then (fn [result]
                 (if-let [rows (.-values result)]
                   (when (pos? (.-length rows))
                     (.-session (aget rows 0)))
                   nil)))
        (.catch (fn [err]
                  (js/console.error "Failed to load session:" err)
                  nil))))
  (save! [this data]
    (-> (.run CapacitorSQLite (clj->js {:database db-name
                                        :statement "INSERT OR REPLACE INTO sessions (id, session) VALUES (1, ?)"
                                        :values [data]
                                        :transaction false}))
        (.catch (fn [err]
                  (js/console.error "Failed to save session:" err)
                  (throw err))))))

(defn create []
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
               (->SQLiteBackend)))
      (.catch (fn [err]
                (js/console.error "Failed to initialize database:" err)
                (throw err)))))
