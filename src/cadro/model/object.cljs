(ns cadro.model.object
  (:require
   [cadro.db :as db]
   [clojure.spec.alpha :as s]))

(s/def ::object (s/keys :req [::id] :opt [::display-name]))

(s/def ::id uuid?)
(s/def ::display-name string?)

(db/register-schema!
  {::id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   ::display-name
   {:db/cardinality :db.cardinality/one}})
