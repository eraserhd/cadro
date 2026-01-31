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
  "Insert triple, retracting any pre-existing values for it."
  [session e a v]
  (let [session        (clara/fire-rules session)
        existing-facts (map :?fact (clara/query session fact-values :?e e :?a a))]
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

(defn reversed-attribute? [kw]
  (and (keyword? kw) (= \_ (first (name kw)))))
(defn reverse-attribute [kw]
  (keyword (namespace kw)
           (if (= \_ (first (name kw)))
             (subs (name kw) 1)
             (str \_ (name kw)))))

(clara/defrule reverse-attributes
  "Make derived reverse-attribute facts"
  [eav/EAV (= e ?e) (= a ?a) (= v ?v) (uuid? ?e) (uuid? ?v) (not (reversed-attribute? ?a))]
  =>
  (clara/insert! (derived ?v (reverse-attribute ?a) ?e)))

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
       (sort-by ::id)))

;; if reference or something that transforms it spans an axis, display it
;; FIXME: dedup?
(clara/defrule children-implicitly-span-parents-axes
  [eav/EAV (= e ?flarg) (= a ::transforms) (= v ?object)]
  [eav/EAV (= e ?flarg) (= a ::spans) (= v ?axis)]
  =>
  (clara/insert! (derived ?object ::spans ?axis)))

(clara-eql/defrule axes-rule
  :query
  [::id
   ::displays-as
   ::raw-count]
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

(defn new-machine-tx [session]
  (let [machine-id (random-uuid)
        point-id   (random-uuid)]
    {:id      machine-id
     :session (-> session
                  (clara/insert (asserted machine-id ::displays-as "New Machine")
                                (asserted machine-id ::transforms  point-id)
                                (asserted point-id ::displays-as "Origin")
                                (asserted point-id ::coordinates {}))
                  (set-reference point-id))}))

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
                                          (str/replace #"[;\s]+" ";")
                                          (str/replace #"^;+" ""))
        [to-process new-scale-values] (loop [to-process       to-process
                                             new-scale-values {}]
                                        (if-let [[_ axis value-str left] (re-matches #"^([a-zA-Z])(-?\d+(?:\.\d*)?);(.*)" to-process)]
                                          (recur left (assoc new-scale-values axis (* value-str 1.0)))
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
       (sort-by ::displays-as)))

(clara/defquery fixture-scales [?fixture-id]
  [eav/EAV (= e ?fixture-id) (= a ::spans) (= v ?scale-id)])

;; Offset of a point with coordinates from current scale position in N-dimensional space.
(s/def ::distance ::tr/locus)

(s/def ::transformed-axes ::tr/locus)

(defrecord Axis [displays-as raw-count])
(defrecord AxisMap [axis-map])

(clara/defrule global-axes
  [eav/EAV (= e ?scale-id) (= a ::raw-count) (= v ?raw-count)]
  [eav/EAV (= e ?scale-id) (= a ::displays-as) (= v ?displays-as)]
  =>
  (clara/insert! (->Axis ?displays-as ?raw-count)))

(clara/defrule global-axis-map-rule
  [?all-axes <- (acc/all) :from [Axis]]
  =>
  (clara/insert! (->AxisMap (into {}
                                  (map (juxt :displays-as :raw-count))
                                  ?all-axes))))

(clara/defrule root-flarg-distances
  [AxisMap (= axis-map ?axis-map)]
  [eav/EAV (= e ?root) (= a ::transforms)]
  (not [eav/EAV (= a ::transforms) (= v ?root)])
  =>
  (clara/insert! (derived ?root ::transformed-axes ?axis-map)))

(clara/defrule derived-distances
  [eav/EAV (= e ?node) (= a ::transformed-axes) (= v ?axis-map)]
  [eav/EAV (= e ?node) (= a ::transforms) (= v ?transformed)]
  =>
  (clara/insert! (derived ?transformed ::transformed-axes ?axis-map)))

(clara/defrule distance
  [eav/EAV (= e ?p) (= a ::transformed-axes) (= v ?axis-map)]
  [eav/EAV (= e ?p) (= a ::coordinates)      (= v ?coords)]
  =>
  (clara/insert! (derived ?p ::distance (tr/- ?coords ?axis-map))))
