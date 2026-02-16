(ns cadro.model
  (:require-macros
   [net.eraserhead.clara-eql.core :as clara-eql])
  (:require
   [cadro.model.facts :as facts]
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clara.rules.accumulators :as acc]
   [clara-eav.eav :as eav]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [medley.core :as medley]
   [net.eraserhead.clara-eql.core :as clara-eql]
   [net.eraserhead.clara-eql.pull :as pull]))

;;-------------------------------------------------------------------------------

(defrecord InvariantError [error extra])

(clara/defquery errors-query []
  [?error <- InvariantError])

(defn errors [session]
  (map :?error (clara/query session errors-query)))

;;-------------------------------------------------------------------------------

(clara/defrule model-id-attributes
  "Pull expressions will need access to ::id as an attribute."
  [(acc/exists) :from [eav/EAV (= e ?id)]]
  =>
  (clara/insert! (facts/derived ?id ::id ?id)))

;;-------------------------------------------------------------------------------

(def schema
  [(facts/derived ::id               :db/unique      :db.unique/identity)
   (facts/derived ::spans            :db/cardinality :db.cardinality/many)
   (facts/derived ::transforms       :db/cardinality :db.cardinality/many)])

;; Display name is a common concept everywhere.
(s/def ::displays-as string?)
(s/def ::display-order number?)

;; Something can span an axis, meaning coordinates extend into it.
(s/def ::spans (s/coll-of (s/keys :req [::id ::displays-as])))

;;-------------------------------------------------------------------------------

;; Is this the current reference point, used to computed displayed coordinates?
(s/def ::reference? boolean?)

(clara/defrule only-one-reference
  [?refcount <- (acc/count) :from [eav/EAV (= a ::reference?)]]
  [:test (<= 2 ?refcount)]
  =>
  (clara/insert! (->InvariantError "more than one reference point in session" {:count ?refcount})))

(clara/defrule reference-has-coordinates
  [eav/EAV (= e ?eid) (= a ::reference?) (= v true)]
  [:not [eav/EAV (= e ?eid) (= a ::coordinates)]]
  =>
  (clara/insert! (->InvariantError "reference point does not have coordinates" {:id ?eid})))

(clara/defrule points-always-have-display-order
  [eav/EAV (= e ?e) (= a ::coordinates)]
  [:not [eav/EAV (= e ?e) (= a ::display-order)]]
  =>
  (clara/insert! (->InvariantError "Point does not have display-order" {:id ?e})))

(clara/defquery reference-query []
  [?ref <- eav/EAV (= e ?id) (= a ::reference?)])

(s/fdef set-reference
  :args (s/cat :session any?
               :id      uuid?)
  :ret  any?)
(defn set-reference [session id]
  (as-> session $
    (clara/fire-rules $)
    (apply clara/retract $ (when-let [r (:?ref (first (clara/query $ reference-query)))]
                             [r]))
    (clara/insert $ (facts/asserted id ::reference? true))))

(defn reference [session]
  (:?id (first (clara/query session reference-query))))

;;FIXME: clara-eql needs recursion
(clara-eql/defrule fixtures-and-points-trees-rule
  :query
  [::id
   ::displays-as
   ::reference?
   ::coordinates
   ::distance
   {::spans
    [::id
     ::displays-as]}
   {::transforms ;...
    [::id
     ::displays-as
     ::display-order
     ::reference?
     ::coordinates
     ::distance
     {::spans
      [::id
       ::displays-as]}]}]
     ;{::transforms ...} ;;FIXME: clara-eql does not like?
  :from ?eid
  :where
  [eav/EAV (= e ?eid) (= a ::transforms)]
  (not [eav/EAV (= a ::transforms) (= v ?eid)]))

(clara/defquery fixtures-and-points-trees-query []
  [clara-eql/QueryResult (= query `fixtures-and-points-trees-rule) (= result ?data)])

(defn fixtures-and-points-trees [session]
  (->> (clara/query session fixtures-and-points-trees-query)
       (map :?data)
       (sort-by ::id)
       (map (fn [fixture]
              (update fixture ::transforms #(sort-by ::display-order %))))))

;; if reference or something that transforms it spans an axis, display it
;; FIXME: dedup?
(clara/defrule children-implicitly-span-parents-axes
  [eav/EAV (= e ?thing) (= a ::transforms) (= v ?object)]
  [eav/EAV (= e ?thing) (= a ::spans) (= v ?axis)]
  =>
  (clara/insert! (facts/derived ?object ::spans ?axis)))

;; ------

;;FIXME: Does not handled nested fixture transforms.
(clara/defrule local-transform-on-points
  [eav/EAV (= e ?fixture) (= a ::transforms) (= v ?p)]
  [eav/EAV (= e ?fixture) (= a ::transform)  (= v ?tr)]
  =>
  (clara/insert! (facts/derived ?p ::local-transform ?tr)))

(clara/defrule local-transform-on-points2
  [eav/EAV (= e ?fixture) (= a ::transforms) (= v ?p)]
  [:not [eav/EAV (= e ?fixture) (= a ::transform)]]
  =>
  (clara/insert! (facts/derived ?p ::local-transform {})))

(clara/defrule transformed-axis-count
  [eav/EAV (= e ?ref) (= a ::reference?) (= v true)]
  [eav/EAV (= e ?ref) (= a ::local-transform) (= v ?tr)]
  [eav/EAV (= e ?ref) (= a ::coordinates) (= v ?coord)]
  [eav/EAV (= e ?axis) (= a :cadro.model.scales/raw-count) (= v ?count)]
  [eav/EAV (= e ?axis) (= a ::displays-as) (= v ?name)]
  =>
  (let [transformed-count (-> {?name ?count}
                              (tr/transform ?tr)
                              (tr/- ?coord)
                              (get ?name))]
    (clara/insert! (facts/derived ?axis ::transformed-count transformed-count))))

;; -------

(clara-eql/defrule axes-rule
  :query
  [::id
   ::displays-as
   ::transformed-count]
  :from ?axis
  :where
  [eav/EAV (= e ?ref) (= a ::reference?) (= v true)]
  [eav/EAV (= e ?ref) (= a ::spans) (= v ?axis)])

(clara/defquery axes-query []
  [clara-eql/QueryResult (= query `axes-rule) (= result ?data)])

(defn axes [session]
  (->> (clara/query session axes-query)
       (map :?data)
       (sort-by ::displays-as)))

;; A tranforms B if A is a Flarg and B is a point or Flarg that is affected by the transformation.
(s/def ::transforms (s/coll-of (s/keys :req [::id])))

;; A coordinate in N-dimensional space.
(s/def ::coordinates ::tr/locus)

(defn new-fixture
  [session {:keys [fixture-id point-id]}]
  {:pre [(uuid? fixture-id)
         (uuid? point-id)]}
  {:id      fixture-id
   :session (-> session
                (clara/insert (facts/asserted fixture-id ::displays-as "New Machine")
                              (facts/asserted fixture-id ::transforms  point-id)
                              (facts/asserted point-id ::displays-as "Origin")
                              (facts/asserted point-id ::display-order 0)
                              (facts/asserted point-id ::coordinates {}))
                (set-reference point-id))})


;; Offset of a point with coordinates from current reference point
(s/def ::distance ::tr/locus)

(s/def ::transformed-axes ::tr/locus)

(clara/defrule distance-from-reference
  [eav/EAV (= e ?ref) (= a ::reference?)      (= v true)]
  [eav/EAV (= e ?ref) (= a ::coordinates)     (= v ?ref-coordinates)]
  [eav/EAV (= e ?ref) (= a ::local-transform) (= v ?ref-local-transform)]
  [eav/EAV (= e ?p)   (= a ::coordinates)     (= v ?p-coordinates)]
  [eav/EAV (= e ?p)   (= a ::local-transform) (= v ?p-local-transform)]
  =>
  (clara/insert!
   (facts/derived ?p ::distance (tr/- ?p-coordinates
                                      (-> ?ref-coordinates
                                          (tr/transform (tr/inverse ?ref-local-transform))
                                          (tr/transform ?p-local-transform))))))

(clara/defquery store-scale-to-reference-q
  [?scale-id]
  [eav/EAV (= e ?scale-id) (= a ::displays-as) (= v ?displays-as)]
  [eav/EAV (= e ?scale-id) (= a :cadro.model.scales/raw-count) (= v ?raw-count)]
  [eav/EAV (= e ?ref-id) (= a ::reference?) (= v true)]
  [eav/EAV (= e ?ref-id) (= a ::coordinates) (= v ?coordinates)]
  [eav/EAV (= e ?ref-id) (= a ::local-transform) (= v ?local-tr)])

(defn store-scale-to-reference [session scale-id]
  (let [{:keys [?displays-as
                ?raw-count
                ?ref-id
                ?coordinates
                ?local-tr]}
        (first (clara/query session store-scale-to-reference-q :?scale-id scale-id))
        tr-count    (get (tr/transform {?displays-as ?raw-count} ?local-tr) ?displays-as)
        coordinates (assoc ?coordinates ?displays-as tr-count)]
    (facts/upsert session ?ref-id ::coordinates coordinates)))

(defrecord ChildDisplayOrder [fixture-id display-order])

(clara/defrule max-display-order
  [eav/EAV (= e ?parent) (= a ::transforms) (= v ?child)]
  [eav/EAV (= e ?child) (= a ::display-order) (= v ?display-order)]
  =>
  (clara/insert! (->ChildDisplayOrder ?parent ?display-order)))

(clara/defquery drop-pin-q []
  [eav/EAV (= e ?ref-id)     (= a ::reference?)      (= v true)]
  [eav/EAV (= e ?ref-id)     (= a ::spans)           (= v ?scale-id)]
  [eav/EAV (= e ?ref-id)     (= a ::local-transform) (= v ?ref-tr)]
  [eav/EAV (= e ?fixture-id) (= a ::transforms)      (= v ?ref-id)]
  [eav/EAV (= e ?scale-id)   (= a ::displays-as)     (= v ?displays-as)]
  [eav/EAV (= e ?scale-id)   (= a :cadro.model.scales/raw-count)       (= v ?raw-count)]
  [?max-display-order <- (acc/max :display-order) :from [ChildDisplayOrder (= fixture-id ?fixture-id)]])

(defn drop-pin [session new-pin-id]
  (let [results                      (clara/query session drop-pin-q)
        {:keys [?ref-tr
                ?fixture-id
                ?max-display-order]} (first results)
        global-coordinates           (into {}
                                           (map (fn [{:keys [?displays-as ?raw-count]}]
                                                  [?displays-as ?raw-count]))
                                           results)
        coordinates                  (tr/transform global-coordinates ?ref-tr)]
    (-> session
        (clara/insert-all [(facts/asserted ?fixture-id ::transforms new-pin-id)
                           (facts/asserted new-pin-id ::displays-as "A")
                           (facts/asserted new-pin-id ::display-order (inc ?max-display-order))
                           (facts/asserted new-pin-id ::coordinates coordinates)])
        (set-reference new-pin-id))))
