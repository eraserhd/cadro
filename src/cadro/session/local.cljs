(ns cadro.session.local
  "localStorage-based storage operations for session data."
  (:require
   [cadro.session.storage :as storage]))

(defrecord LocalStorageBackend []
  storage/SessionStorage
  (load! [this]
    (js/Promise.resolve
     (.getItem js/localStorage "session")))
  (save! [this data]
    (js/Promise.resolve
     (.setItem js/localStorage "session" data))))

(defn create []
  (js/Promise.resolve (->LocalStorageBackend)))
