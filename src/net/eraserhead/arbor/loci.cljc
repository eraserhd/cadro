(ns net.eraserhead.arbor.loci
 (:refer-clojure :exclude [conj get update])
 (:require
  [clojure.spec.alpha :as s]
  [net.eraserhead.arbor.scale :as scale]))

(s/def ::id uuid?)
(s/def ::name string?)
(s/def ::parent (s/nilable ::id))
(s/def ::locus (s/keys :req [::id ::name ::parent]))

(s/def ::loci (s/map-of ::id ::locus))
(s/def ::origin (s/nilable ::id))

;; Computed values
(s/def ::children (s/coll-of ::loci))
(s/def ::origin? boolean?)

(defn- valid-origin? [{:keys [::loci ::origin]}]
  (if (empty? loci)
    (nil? origin)
    (contains? loci origin)))

(defn- valid-parents? [{:keys [::loci]}]
  (every?
   (fn [{:keys [::parent]}]
     (or (nil? parent)
         (contains? loci parent)))
   loci))

(s/def ::db (s/and
             (s/keys :req [::loci ::origin])
             valid-origin?
             valid-parents?))

(def empty-db
  "A db with no loci in it."
  {::loci  {},
   ::origin nil})

(defn get
  "Retrieves a loci from the db by id."
  [db id]
  (get-in db [::loci id]))

(defn origin
  "Retrieves the origin loci from the db."
  [{:keys [::origin], :as db}]
  (get db origin))

(defn update
  "Apply f to the locus in the database with id.

  Preserves invariants about selected origin and order."
  [{:keys [::origin], :as db} id f & args]
  {:pre [(s/assert ::db db)
         (s/assert ::id id)]
   :post [(s/assert ::db %)]}
  (cond-> db
    true          (update-in [::loci id] #(apply f % args))
    (nil? origin) (assoc ::origin id)))

(defn conj
  "Adds a new loci to the db."
  [db {:keys [::id], :as locus}]
  {:pre [(s/assert ::db db)
         (s/assert ::locus locus)]
   :post [(s/assert ::db %)]}
  (update db id (constantly locus)))

(defn top-level
  "A lazy sequence of top-level nodes, in display order."
  [{:keys [::loci]}]
  (->> (vals loci)
       (filter (comp nil? ::parent))))

(defn tree
  "A lazy sequence of top-level nodes, with ::children, in display order."
  ([db]
   (tree db nil))
  ([{:keys [::loci ::origin], :as db} parent]
   (->> (vals loci)
        (filter #(= (::parent %) parent))
        (map (fn [{:keys [::id], :as loci}]
               (assoc loci ::children (tree db id))))
        (map (fn [{:keys [::id], :as loci}]
               (assoc loci ::origin? (= id origin)))))))

(defn origin-stack
  "A list of ancestors of the origin node, inclusive."
  [{:keys [::origin ::loci], :as db}]
  {:pre [(s/assert ::db db)]}
  (->> (iterate (comp loci ::parent) (loci origin))
       (take-while some?)
       reverse))
