(ns cadro.model
  (:require
   [cadro.db :as db]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(db/register-schema!
  {::id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   ::display-name
   {:db/cardinality :db.cardinality/one}
   ::spans
   {:db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}
   ::reference?
   {:db/cardinality :db.cardinality/one}
   ::transforms
   {:db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}
   ::controller
   {:db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}})

;; All objects should have an id?
(s/def ::id uuid?)

;; Display name is a common concept everywhere.
(s/def ::display-name string?)

;; Something can span an axis, meaning coordinates extend into it.
(s/def ::spans (s/coll-of (s/keys :req [::id ::display-name])))

;; Is this the current reference point, used to computed displayed coordinates?
(s/def ::reference? boolean?)
(defmethod db/invariants ::only-one-reference?
  [db]
  (let [eids (d/q '[:find [?eid ...]
                    :where [?eid ::reference? true]]
                  db)]
    (when (<= 2 (count eids))
      [{:error "More than one entity tagged with ::model/reference?."
        :eids eids}])))

(def reference-id-q
  '[:find ?eid .
    :where
    [?eid ::reference? true]])

(def root-path-pull
  '[::position
    {::_transforms ...
     ::spans
     [::display-name
      ::raw-count]}])

(defn root-path-axes
  [root-path]
  (->> (iterate (comp first ::_transforms) root-path)
       (take-while some?)
       (mapcat ::spans)
       (sort-by ::display-name)))

(defn set-reference?-tx
  [ds reference-id]
  (concat
    (for [eid (d/q '[:find [?eid ...]
                     :in $ ?reference-id
                     :where
                     [?eid ::reference? ?value]
                     (not [(= ?eid ?reference-id)])]
                   ds
                   reference-id)]
      [:db/retract eid ::reference?])
    [[:db/add reference-id ::reference? true]]))

;; A tranforms B if A is a Flarg and B is a point or Flarg that is affected by the transformation.
(s/def ::transforms (s/coll-of (s/keys :req [::id])))

;; A coordinate in N-dimensional space.
(s/def ::position (s/map-of string? number?))
(defmethod db/invariants ::reference?-has-position
  [db]
  (when-let [eids (seq (d/q '[:find [?eid ...]
                              :where
                              [?eid ::reference? true]
                              (not [?eid ::position])]
                            db))]
    [{:error "::model/reference? point does not have ::model/position."
      :eids eids}]))

;; A scale has a controller, which is what we connect to.  Multiple scales can share one.
(s/def ::controller (s/keys :req [::id]))

;; A untranslated reading, as from a scale.
(s/def ::raw-count number?)

(defn upsert-raw-count-tx
  [ds controller-id scale-name value]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::id ?id]
                             [?e ::display-name ?name]
                             [?e ::controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::id           (or (get name->id scale-name)
                         (db/squuid))
      ::display-name scale-name
      ::raw-count    value
      ::controller   controller-id}]))

