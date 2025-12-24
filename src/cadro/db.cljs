(ns cadro.db
  (:require
   [datascript.core :as d]
   [re-posh.core :as re-posh]))

(defmulti model-schema
  "Registration of schema."
  #(throw (ex-info "This is used for registration and shouldn't actually be called." {})))

(defn schema
  "Retrieves all registered schema."
  []
  (->> (methods model-schema)
       vals
       (map #(%))
       (reduce merge)))

(def ^:dynamic *conn* nil)

(defn connect!
  "Creates a database connection with all registered schema."
  []
  (let [conn (d/create-conn (schema))]
    (re-posh/connect! conn)
    (set! *conn* conn)
    conn))
