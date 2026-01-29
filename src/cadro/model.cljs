(ns cadro.model
  (:require-macros
   [net.eraserhead.clara-eql.core :as clara-eql])
  (:require
   [cadro.db :as db]
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clara.rules.accumulators :as acc]
   [clara-eav.eav :as eav]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datascript.core :as d]
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

;;-------------------------------------------------------------------------------

(def schema
  [(derived ::id               :db/unique      :db.unique/identity)
   (derived ::spans            :db/cardinality :db.cardinality/many)
   (derived ::transforms       :db/cardinality :db.cardinality/many)
   (derived ::hardware-address :db/unique      :db.unique/identity)])

(db/register-schema!
  {::id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   ::displays-as
   {:db/cardinality :db.cardinality/one}
   ::spans
   {:db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}
   ::reference?
   {:db/cardinality :db.cardinality/one}
   ::transforms
   {:db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}
   ::controller
   {:db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}
   ::connection-status
   {:db/cardinality :db.cardinality/one}
   ::hardware-address
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}})

;; Display name is a common concept everywhere.
(s/def ::displays-as string?)


;; Something can span an axis, meaning coordinates extend into it.
(s/def ::spans (s/coll-of (s/keys :req [::id ::displays-as])))


(defn associate-scale-tx
  [ds fixture-id scale-id]
  [[:db/add fixture-id ::spans scale-id]])

(defn dissociate-scale-tx
  [ds fixture-id scale-id]
  [[:db/retract fixture-id ::spans scale-id]])

;;-------------------------------------------------------------------------------

;; Is this the current reference point, used to computed displayed coordinates?

(clara/defrule only-one-reference
  [?refcount <- (acc/count) :from [eav/EAV (= a ::reference?)]]
  [:test (<= 2 ?refcount)]
  =>
  (clara/insert! (->InvariantError "more than one reference point in session" {:count ?refcount})))

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

(def root-path-pull
  '[::coordinates
    {::_transforms ...}
    {::spans
     [::id
      ::displays-as
      ::raw-count]}])

;;FIXME: clara-eql needs recursion
(clara-eql/defrule fixtures-and-points-trees-rule
  :query
  [::id
   ::displays-as
   ::reference?
   ::coordinates
   {::transforms ;...
    [::id
     ::displays-as
     ::reference?
     ::coordinates]}]
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

(defn root-path-axes
  [root-path]
  (->> (iterate (comp first ::_transforms) root-path)
       (take-while some?)
       (mapcat ::spans)
       (sort-by ::displays-as)))

;; A tranforms B if A is a Flarg and B is a point or Flarg that is affected by the transformation.
(s/def ::transforms (s/coll-of (s/keys :req [::id])))

;; A coordinate in N-dimensional space.
(s/def ::coordinates (s/map-of string? number?))

(clara/defrule reference-has-coordinates
  [eav/EAV (= e ?eid) (= a ::reference?) (= v true)]
  [:not [eav/EAV (= e ?eid) (= a ::coordinates)]]
  =>
  (clara/insert! (->InvariantError "reference point does not have coordinates" {:id ?eid})))

(defn propagate-spans
  "Collects spans from more-root elements and marks all things and points."
  [tree-list]
  (map (fn propagate-over-tree
         ([tree] (propagate-over-tree tree []))
         ([tree spans]
          (let [spans (->> (concat spans (::spans tree))
                           (medley/distinct-by ::id)
                           (into []))]
            (-> tree
              (assoc ::spans spans)
              (medley/update-existing ::transforms (fn [transforms]
                                                     (mapv #(propagate-over-tree % spans) transforms)))))))
       tree-list))

(defn- globalized-tree-reference [tree]
  (cond
    (::reference? tree) tree
    (::coordindates tree)   nil
    :else               (first (keep globalized-tree-reference (::transforms tree)))))

(defn- add-distance1
  ([tree]
   (add-distance1 tree (globalized-tree-reference tree)))
  ([tree {rpos ::coordinates, :as r}]
   (if-let [p (::coordinates tree)]
     (assoc tree ::distance (tr/- p rpos))
     (update tree ::transforms (fn [transforms]
                                 (mapv #(add-distance1 % r) transforms))))))

(defn add-distances
  [tree-list]
  (map add-distance1 tree-list))

(defn new-machine-tx
  [ds session]
  (let [machine-id (random-uuid)
        point-id   (random-uuid)]
    {:id      machine-id
     :tx      [{::id           machine-id
                ::displays-as "New Machine"
                ::transforms   [{::id point-id
                                 ::displays-as "Origin"
                                 ::coordinates {}}]}]
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

(defn- next-uuid [uuids]
  (let [result (first @uuids)]
    (swap! uuids rest)
    result))

(defn upsert-raw-count-tx
  [ds controller-id scale-name value uuids]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::id ?id]
                             [?e ::displays-as ?name]
                             [?e ::controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::id           (or (get name->id scale-name)
                         (next-uuid uuids))
      ::displays-as scale-name
      ::raw-count    value
      ::controller   controller-id}]))

;; Bluetooth, ethernet, or whatever address.
(s/def ::hardware-address string?)

;; Connection status for hardware
(s/def ::connection-status #{:disconnected
                             :connecting
                             :connected})

(defn set-connection-status-tx
  [controller-id status]
  {:pre [(s/valid? ::connection-status status)]}
  [[:db/add controller-id ::connection-status status]])

;; Unprocessed, received data
(s/def ::receive-buffer string?)

(defn add-controllers-tx
  [ds controller-list uuids]
  {:pre [(d/db? ds)
         (s/valid? (s/coll-of (s/keys :req [::displays-as ::hardware-address])) controller-list)]}
  (let [addr->controller (into {}
                               (map (juxt ::hardware-address identity))
                               (d/q '[:find [(pull ?obj [::id ::hardware-address ::connection-status]) ...]
                                      :where [?obj ::hardware-address]]
                                    ds))]
    (mapv (fn [{:keys [::hardware-address], :as scale-controller}]
            (let [{:keys [::id ::connection-status]} (get addr->controller hardware-address)
                  new-status                     (or connection-status :disconnected)
                  new-id                         (or id (next-uuid uuids))]
              (assoc scale-controller
                     ::id new-id
                     ::connection-status new-status)))
          controller-list)))

(clara/defquery controllers []
  [eav/EAV (= e ?id) (= a ::displays-as)       (= v ?displays-as)]
  [eav/EAV (= e ?id) (= a ::hardware-address)  (= v ?hardware-address)]
  [eav/EAV (= e ?id) (= a ::connection-status) (= v ?connection-status)])

(defn insert-controllers
  [session controller-list uuids]
  {:pre [(s/valid? (s/coll-of (s/keys :req [::displays-as ::hardware-address])) controller-list)]}
  (let [existing-controllers (->> (clara/query session controllers)
                                  (group-by :?hardware-address))]
    (reduce (fn [session {:keys [::displays-as ::hardware-address]}]
              (let [{:keys [?id ?connection-status]} (get-in existing-controllers [hardware-address 0])
                    id                               (or ?id (next-uuid uuids))
                    connection-status                (or ?connection-status :disconnected)]
                (-> session
                    (upsert id ::displays-as displays-as)
                    (upsert id ::hardware-address hardware-address)
                    (upsert id ::connection-status connection-status))))
            session
            controller-list)))

(defn add-received-data-tx
  [ds controller-id data uuids]
  {:pre [(d/db? ds)
         (string? data)]}
  (let [to-process                    (-> (str (::receive-buffer (d/entity ds controller-id))
                                               data)
                                          (str/replace #"[;\s]+" ";")
                                          (str/replace #"^;+" ""))
        [to-process new-scale-values] (loop [to-process       to-process
                                             new-scale-values {}]
                                        (if-let [[_ axis value-str left] (re-matches #"^([a-zA-Z])(-?\d+(?:\.\d*)?);(.*)" to-process)]
                                          (recur left (assoc new-scale-values axis (* value-str 1.0)))
                                          [to-process new-scale-values]))]
    (concat
      [[:db/add controller-id ::receive-buffer to-process]]
      (mapcat (fn [[scale-name value]]
                (upsert-raw-count-tx ds controller-id scale-name value uuids))
              new-scale-values))))

(clara/defquery scale-id [?controller-id ?scale-name]
  [eav/EAV (= e ?scale-id) (= a ::controller) (= v ?controller-id)]
  [eav/EAV (= e ?scale-id) (= a ::displays-as) (= v ?scale-name)])

(defn upsert-raw-count [session controller-id scale-name value uuids]
  (if-let [id (:?scale-id (first (clara/query session scale-id :?controller-id controller-id :?scale-name scale-name)))]
    (upsert session id ::raw-count value)
    (let [id (next-uuid uuids)]
      (-> session
          (upsert id ::displays-as scale-name)
          (upsert id ::raw-count value)
          (upsert id ::controller controller-id)))))

(defn add-received-data [session controller-ref data uuids]
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
              (upsert-raw-count session controller-id scale-name value uuids))
            (upsert session controller-id ::receive-buffer to-process)
            new-scale-values)))
