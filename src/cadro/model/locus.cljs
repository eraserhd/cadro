(ns cadro.model.locus
  "Model of loci, or positions (potentially with transformations).

  The object with ident ::global has a ::reference attribute which defines
  the current global reference locus for displaying coordinates."
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.scale :as scale]
   [clojure.spec.alpha :as s]
   [datascript.core :as d]))

(def global ::global)

(db/register-schema!
  {::reference   {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}})

(s/def ::locus
 (s/keys :req [::model/id
               ::offset]
         :opt [::model/display-name
               ::model/spans]))

(s/def ::offset (s/map-of string? number?))

(s/def ::reference ::locus)
(s/def ::global (s/keys :opt [::reference]))

(defn set-reference-tx [eid]
  [{:db/ident global
    ::reference eid}])

(defn new-machine-tx []
  (let [id (db/squuid)]
    {:id [::model/id id]
     :tx (concat
          [{::model/id           id
            ::model/display-name "New Machine"
            ::offset              {"x" 42}}]
          (set-reference-tx [::model/id id]))}))

(defn associate-scale-tx
  [ds locus-id scale-id]
  [[:db/add locus-id ::model/spans scale-id]])

(defn dissociate-scale-tx
  [ds locus-id scale-id]
  [[:db/retract locus-id ::model/spans scale-id]])
