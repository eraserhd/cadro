(ns cadro.model.locus
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(db/register-schema!
  {::origin {:db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one}})

(s/def ::locus (s/and ::object/object (s/keys :req [::offset] :opt [::origin])))

(s/def ::offset (s/map-of string? number?))
(s/def ::origin ::locus)

(defn new-machine-tx []
  (let [id (random-uuid)]
    {:id [::object/id id]
     :tx [{::object/id           id
           ::object/display-name "New Machine"
           ::offset              {"x" 42}}]}))
