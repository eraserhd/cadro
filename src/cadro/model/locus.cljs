(ns cadro.model.locus
  "Model of loci, or positions (potentially with transformations).

  The object with ident ::global has a ::reference attribute which defines
  the current global reference locus for displaying coordinates."
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(def global ::global)

(db/register-schema!
  {::origin      {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}
   ::reference   {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}
   ::locus-scale {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/many
                  :db/isComponent true}})

(s/def ::locus (s/and ::object/object (s/keys :req [::offset] :opt [::origin])))

(s/def ::offset (s/map-of string? number?))
(s/def ::origin ::locus)

(s/def ::locus-scale (s/keys :req [::scale/scale]))

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

(defn associate-scale-tx
  [ds locus-id scale-id]
  (let [id (d/q '[:find ?id .
                  :in $ ?locus-id ?scale-id
                  :where
                  [?locus-id ::locus-scale ?e]
                  [?e ::scale/scale ?scale-id]
                  [?e ::object/id ?id]]
                ds
                locus-id
                scale-id)]
    [{:db/id locus-id
      ::locus-scale {::object/id (or id (d/squuid))
                     ::scale/scale scale-id}}]))

(defn dissociate-scale-tx
  [ds locus-id scale-id]
  (let [locus-scale (d/q '[:find ?e .
                           :in $ ?locus-id ?scale-id
                           :where
                           [?locus-id ::locus-scale ?e]
                           [?e ::scale/scale ?scale-id]
                           [?e ::object/id ?id]]
                         ds
                         locus-id
                         scale-id)]
    (when locus-scale
      [[:db/retractEntity locus-scale]])))
