(ns cadro.model
  (:require
   [cadro.db :as db]
   [clojure.spec.alpha :as s]))

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
   {:db/cardinality :db.cardinality/one}})

;; All objects should have an id?
(s/def ::id uuid?)

;; Display name is a common concept everywhere.
(s/def ::display-name string?)

;; Something can span an axis, meaning coordinates extend into it.
(s/def ::spans (s/coll-of (s/keys :req [::id ::display-name])))

;; Is this the current reference point, used to computed displayed coordinates?
(s/def ::reference? boolean?)
