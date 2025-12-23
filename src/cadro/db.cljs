(ns cadro.db
  (:require
   [datascript.core :as d]
   [posh.reagent :as p]))

(def conn (d/create-conn))
(p/posh! conn)
(js/console.log "registered with posh!")
