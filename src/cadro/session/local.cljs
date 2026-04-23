(ns cadro.session.local
  "localStorage-based storage operations for session data."
  (:require
   [cadro.session.storage :as storage]))

(defrecord LocalStorageBackend []
  storage/SessionStorage
  (save! [this data]
    (js/Promise.resolve
     (.setItem js/localStorage "session" data)))

  (load! [this]
    (js/Promise.resolve
     (.getItem js/localStorage "session"))))

(defn create []
  (->LocalStorageBackend))
