(ns cadro.model.locus
  (:require
   [cadro.model.object :as object]
   [clojure.spec.alpha :as s]))

(def spec
  {::origin {:db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one}})

(s/def ::locus (s/and ::object/object (s/keys :req [::offset] :opt [::origin])))

(s/def ::offset (s/map-of string? number?))
(s/def ::origin ::locus)
