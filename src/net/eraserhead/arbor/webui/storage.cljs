(ns net.eraserhead.arbor.webui.storage
  (:require
   [cljs.reader]))

(defn- try-read-string [s]
  (try
   (cljs.reader/read-string s)
   (catch js/Error e
     nil)))

(defn load-db
  "Tries to load and parse db from localStorage, or returns nil."
  []
  (some-> js/localStorage
          (.getItem "db")
          try-read-string))

(defn store-db
  [db]
  (.setItem js/localStorage "db" (pr-str db)))
