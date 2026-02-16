(ns cadro.model
  (:require-macros
   [net.eraserhead.clara-eql.core :as clara-eql])
  (:require
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clara.rules.accumulators :as acc]
   [clara-eav.eav :as eav]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [medley.core :as medley]
   [net.eraserhead.clara-eql.core :as clara-eql]
   [net.eraserhead.clara-eql.pull :as pull]))

(defn asserted [e a v]
  (assoc (eav/->EAV e a v) :persistent? true))

(defn derived [e a v]
  (eav/->EAV e a v))

(clara/defquery fact-values
  [?e ?a]
  [?fact <- eav/EAV (= ?e e) (= ?a a) (= ?v v)])

(defn upsert
  "Insert triple, retracting any pre-existing values for it.
   Note: Caller should fire-rules before first upsert and after all upserts."
  [session e a v]
  (let [existing-facts (map :?fact (clara/query session fact-values :?e e :?a a))]
    (if (and (= 1 (count existing-facts)) (= v (:?v (first existing-facts))))
      session
      (as-> session $
        (apply clara/retract $ existing-facts)
        (clara/insert $ (asserted e a v))))))

;;-------------------------------------------------------------------------------

(clara/defquery persistent-facts []
  [?fact <- eav/EAV (= e ?e) (= a ?a) (= v ?v)]
  [:test (:persistent? ?fact)])

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
  (clara/insert! (derived ?id ::id ?id)))

;;-------------------------------------------------------------------------------

(def schema
  [(derived ::id               :db/unique      :db.unique/identity)
   (derived ::spans            :db/cardinality :db.cardinality/many)
   (derived ::transforms       :db/cardinality :db.cardinality/many)
   (derived ::hardware-address :db/unique      :db.unique/identity)

   ;; Hrmm, clara-eql can't figure this out?
   (derived ::_controller      :db/cardinality :db.cardinality/many)])

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
    (clara/insert $ (asserted id ::reference? true))))

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
   {::transforms ;...
    [::id
     ::displays-as
     ::display-order
     ::reference?
     ::coordinates
     ::distance]}]
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
  (clara/insert! (derived ?object ::spans ?axis)))

;; ------

;;FIXME: Does not handled nested fixture transforms.
(clara/defrule local-transform-on-points
  [eav/EAV (= e ?fixture) (= a ::transforms) (= v ?p)]
  [eav/EAV (= e ?fixture) (= a ::transform)  (= v ?tr)]
  =>
  (clara/insert! (derived ?p ::local-transform ?tr)))

(clara/defrule local-transform-on-points2
  [eav/EAV (= e ?fixture) (= a ::transforms) (= v ?p)]
  [:not [eav/EAV (= e ?fixture) (= a ::transform)]]
  =>
  (clara/insert! (derived ?p ::local-transform {})))

(clara/defrule transformed-axis-count
  [eav/EAV (= e ?ref) (= a ::reference?) (= v true)]
  [eav/EAV (= e ?ref) (= a ::local-transform) (= v ?tr)]
  [eav/EAV (= e ?ref) (= a ::coordinates) (= v ?coord)]
  [eav/EAV (= e ?axis) (= a ::raw-count) (= v ?count)]
  [eav/EAV (= e ?axis) (= a ::displays-as) (= v ?name)]
  =>
  (let [transformed-count (-> {?name ?count}
                              (tr/transform ?tr)
                              (tr/- ?coord)
                              (get ?name))]
    (clara/insert! (derived ?axis ::transformed-count transformed-count))))

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
                (clara/insert (asserted fixture-id ::displays-as "New Machine")
                              (asserted fixture-id ::transforms  point-id)
                              (asserted point-id ::displays-as "Origin")
                              (asserted point-id ::display-order 0)
                              (asserted point-id ::coordinates {}))
                (set-reference point-id))})

;; A scale has a controller, which is what we connect to.  Multiple scales can share one.
(s/def ::controller (s/keys :req [::id
                                  ::displays-as
                                  ::hardware-address
                                  ::connection-status]
                            :opt [::receive-buffer]))

;; A untranslated reading, as from a scale.
(s/def ::raw-count number?)

;; Bluetooth, ethernet, or whatever address.
(s/def ::hardware-address string?)

;; Connection status for hardware
(s/def ::connection-status #{:disconnected
                             :connecting
                             :connected})

(defn set-connection-status [session controller-id status]
  (upsert session controller-id ::connection-status status))

;; Unprocessed, received data
(s/def ::receive-buffer string?)

(clara/defquery controllers []
  [eav/EAV (= e ?id) (= a ::displays-as)       (= v ?displays-as)]
  [eav/EAV (= e ?id) (= a ::hardware-address)  (= v ?hardware-address)]
  [eav/EAV (= e ?id) (= a ::connection-status) (= v ?connection-status)])

(defn insert-controllers
  [session controller-list]
  {:pre [(s/valid? (s/coll-of (s/keys :req [::displays-as ::hardware-address])) controller-list)]}
  (let [existing-controllers (->> (clara/query session controllers)
                                  (group-by :?hardware-address))]
    (reduce (fn [session {:keys [::displays-as ::hardware-address]}]
              (let [{:keys [?id ?connection-status]} (get-in existing-controllers [hardware-address 0])
                    id                               (or ?id (random-uuid))
                    connection-status                (or ?connection-status :disconnected)]
                (-> session
                    (upsert id ::displays-as displays-as)
                    (upsert id ::hardware-address hardware-address)
                    (upsert id ::connection-status connection-status))))
            session
            controller-list)))

(clara/defquery scale-id [?controller-id ?scale-name]
  [eav/EAV (= e ?scale-id) (= a ::controller) (= v ?controller-id)]
  [eav/EAV (= e ?scale-id) (= a ::displays-as) (= v ?scale-name)])

(defn upsert-raw-count [session controller-id scale-name value]
  (if-let [id (:?scale-id (first (clara/query session scale-id :?controller-id controller-id :?scale-name scale-name)))]
    (upsert session id ::raw-count value)
    (let [id (random-uuid)]
      (-> session
          (upsert id ::displays-as scale-name)
          (upsert id ::raw-count value)
          (upsert id ::controller controller-id)))))

(defn add-received-data [session controller-ref data]
  (let [controller-id                 (pull/entid session controller-ref)
        to-process                    (-> (str (::receive-buffer (pull/pull session [::receive-buffer] controller-id))
                                               data)
                                          (str/replace #"[;\s\u0000]+" ";")
                                          (str/replace #"^;+" ""))
        [to-process new-scale-values] (loop [to-process       to-process
                                             new-scale-values {}]
                                        (if-let [[_ axis value-str left] (re-matches #"^([a-zA-Z])([^;]*);(.*)" to-process)]
                                          (let [axis (str/upper-case axis)]
                                            (if (= "V" axis)
                                              (recur left new-scale-values)
                                              (recur left (assoc new-scale-values axis (* value-str 1.0)))))
                                          [to-process new-scale-values]))]
    (reduce (fn [session [scale-name value]]
              (upsert-raw-count session controller-id scale-name value))
            (upsert session controller-id ::receive-buffer to-process)
            new-scale-values)))

(clara-eql/defrule scales-rule
  :query
  [::id
   ::displays-as
   ::hardware-address
   ::connection-status
   {::_controller [::id
                   ::displays-as
                   ::raw-count]}]
  :from ?controller
  :where
  [eav/EAV (= e ?controller) (= a ::hardware-address)])

(clara/defquery scales-query []
  [clara-eql/QueryResult (= query `scales-rule) (= result ?data)])

(defn scales [session]
  (->> (clara/query session scales-query)
       (map :?data)
       (sort-by ::hardware-address)
       (map (fn [controller]
              (update controller ::_controller (fn [scales]
                                                 (sort-by ::displays-as scales)))))))

(clara/defquery fixture-scales [?fixture-id]
  [eav/EAV (= e ?fixture-id) (= a ::spans) (= v ?scale-id)])

;; Offset of a point with coordinates from current reference point
(s/def ::distance ::tr/locus)

(s/def ::transformed-axes ::tr/locus)

(clara/defrule foo
  [eav/EAV (= e ?ref) (= a ::reference?)      (= v true)]
  [eav/EAV (= e ?ref) (= a ::coordinates)     (= v ?ref-coordinates)]
  [eav/EAV (= e ?ref) (= a ::local-transform) (= v ?ref-local-transform)]
  [eav/EAV (= e ?p)   (= a ::coordinates)     (= v ?p-coordinates)]
  [eav/EAV (= e ?p)   (= a ::local-transform) (= v ?p-local-transform)]
  =>
  (clara/insert!
   (derived ?p ::distance (tr/- ?p-coordinates
                                (-> ?ref-coordinates
                                    (tr/transform (tr/inverse ?ref-local-transform))
                                    (tr/transform ?p-local-transform))))))

(clara/defquery store-scale-to-reference-q
  [?scale-id]
  [eav/EAV (= e ?scale-id) (= a ::displays-as) (= v ?displays-as)]
  [eav/EAV (= e ?scale-id) (= a ::raw-count) (= v ?raw-count)]
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
    (upsert session ?ref-id ::coordinates coordinates)))

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
  [eav/EAV (= e ?scale-id)   (= a ::raw-count)       (= v ?raw-count)]
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
        (clara/insert-all [(asserted ?fixture-id ::transforms new-pin-id)
                           (asserted new-pin-id ::displays-as "A")
                           (asserted new-pin-id ::display-order (inc ?max-display-order))
                           (asserted new-pin-id ::coordinates coordinates)])
        (set-reference new-pin-id))))
