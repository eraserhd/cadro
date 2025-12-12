(ns net.eraserhead.arbor.loci
 (:refer-clojure :exclude [get])
 (:require
  [clojure.spec.alpha :as s]))

(s/def ::id uuid?)
(s/def ::name string?)
(s/def ::parent (s/nilable ::id))
(s/def ::locus (s/keys :req [::id ::name] :opt [::parent]))

(s/def ::loci (s/map-of ::id ::locus))
(s/def ::focus (s/nilable ::id))
(s/def ::db (s/keys :req [::loci ::focus]))

(def empty-db
  "A db with no loci in it."
  {::loci  {},
   ::focus nil})

(defn get
  "Retrieves a loci from the db by id."
  [db id]
  (get-in db [::loci id]))

(defn focused
  "Retrieves the focused loci from the db."
  [{:keys [::focus], :as db}]
  (get db focus))

(defn add-top-level
  "Adds a new loci to the db and focuses it."
  [db {:keys [::id], :as locus}]
  {:pre [(s/assert ::db db)
         (s/assert ::locus locus)]}
  (let [locus (assoc locus ::previous (focused db))]
    (-> db
      (assoc-in [::loci id] locus)
      (assoc ::focus id))))
