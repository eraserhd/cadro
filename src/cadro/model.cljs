(ns cadro.model
  (:require
   [cadro.db :as db]
   [clojure.spec.alpha :as s]))

(db/register-schema!
  {::id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   ::display-name
   {:db/cardinality :db.cardinality/one}})

(s/def ::id uuid?)
(s/def ::display-name string?)
