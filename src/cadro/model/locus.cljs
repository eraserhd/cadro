(ns cadro.model.locus
  "Model of loci, or positions (potentially with transformations).

  The object with ident ::global has a ::reference attribute which defines
  the current global reference locus for displaying coordinates."
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(def global ::global)

(db/register-schema!
  {::origin    {:db/valueType :db.type/ref
                :db/cardinality :db.cardinality/one}
   ::reference {:db/valueType :db.type/ref
                :db/cardinality :db.cardinality/one}})

(s/def ::locus (s/and ::object/object (s/keys :req [::offset] :opt [::origin])))

(s/def ::offset (s/map-of string? number?))
(s/def ::origin ::locus)

(s/def ::reference ::locus)
(s/def ::global (s/keys :opt [::reference]))

(defn set-reference-tx [eid]
  [{:db/ident global
    ::reference eid}])

(defn new-machine-tx []
  (let [id (db/squuid)]
    {:id [::object/id id]
     :tx (concat
          [{::object/id           id
            ::object/display-name "New Machine"
            ::offset              {"x" 42}}]
          (set-reference-tx [::object/id id]))}))
