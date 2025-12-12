(ns net.eraserhead.arbor
  (:require
   [clojure.spec.alpha :as s]
   [net.eraserhead.arbor.loci :as loci]))

(s/def ::app-db (s/keys :req [::loci/db]))

(def initial-state
  {::loci/db loci/empty-db})

(s/assert ::app-db initial-state)
