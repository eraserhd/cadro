(ns cadro.model
  (:require
   [cadro.db :as db]
   [cadro.session :as session]
   [cadro.transforms :as tr]
   [clara.rules :as clara]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datascript.core :as d]
   [medley.core :as medley]))

(defrecord DisplaysAs [id display-name])
(defrecord Spans [id scale])
(defrecord Reference [id])
(defrecord Transforms [id child])
(defrecord Coordinates [id coordinates])
(defrecord ControlsScale [controller-id scale-id])
(defrecord RawCount [scale-id value])
(defrecord HardwareAddress [controler-id address])
(defrecord ConnectionStatus [controller-id status])
(defrecord ReceiveBuffer [controller-id data])

(db/register-schema!
  {::id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   ::display-name
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

;; All objects should have an id?
(s/def ::id uuid?)

;; Display name is a common concept everywhere.
(s/def ::display-name string?)


;; Something can span an axis, meaning coordinates extend into it.
(s/def ::spans (s/coll-of (s/keys :req [::id ::display-name])))


(defn associate-scale-tx
  [ds fixture-id scale-id]
  [[:db/add fixture-id ::spans scale-id]])

(defn dissociate-scale-tx
  [ds fixture-id scale-id]
  [[:db/retract fixture-id ::spans scale-id]])

;; Is this the current reference point, used to computed displayed coordinates?
(s/def ::reference? boolean?)
(defmethod db/invariants ::only-one-reference?
  [db]
  (let [eids (d/q '[:find [?eid ...]
                    :where [?eid ::reference? true]]
                  db)]
    (when (<= 2 (count eids))
      [{:error "More than one entity tagged with ::model/reference?."
        :eids eids}])))

(def reference-id-q
  '[:find ?eid .
    :where
    [?eid ::reference? true]])

(def root-path-pull
  '[::position
    {::_transforms ...
     ::spans
     [::id
      ::display-name
      ::raw-count]}])

(def top-level-fixture-eids-q
  '[:find [?eid ...]
    :where
    [?eid ::transforms]
    (not [_ ::transforms ?eid])])

(def fixtures-and-points-trees-pull
  '[::id
    ::display-name
    ::reference?
    ::position
    {::transforms ...
     ::spans [::id ::display-name]}])

(defn root-path-axes
  [root-path]
  (->> (iterate (comp first ::_transforms) root-path)
       (take-while some?)
       (mapcat ::spans)
       (sort-by ::display-name)))

(defn set-reference?-tx
  [ds reference-id]
  (concat
    (for [eid (d/q '[:find [?eid ...]
                     :in $ ?reference-id
                     :where
                     [?eid ::reference? ?value]
                     (not [(= ?eid ?reference-id)])]
                   ds
                   reference-id)]
      [:db/retract eid ::reference?])
    [[:db/add reference-id ::reference? true]]))

;; A tranforms B if A is a Flarg and B is a point or Flarg that is affected by the transformation.
(s/def ::transforms (s/coll-of (s/keys :req [::id])))

;; A coordinate in N-dimensional space.
(s/def ::position (s/map-of string? number?))
(defmethod db/invariants ::reference?-has-position
  [db]
  (when-let [eids (seq (d/q '[:find [?eid ...]
                              :where
                              [?eid ::reference? true]
                              (not [?eid ::position])]
                            db))]
    [{:error "::model/reference? point does not have ::model/position."
      :eids eids}]))

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
    (::position tree)   nil
    :else               (first (keep globalized-tree-reference (::transforms tree)))))

(defn- add-distance1
  ([tree]
   (add-distance1 tree (globalized-tree-reference tree)))
  ([tree {rpos ::position, :as r}]
   (if-let [p (::position tree)]
     (assoc tree ::distance (tr/- p rpos))
     (update tree ::transforms (fn [transforms]
                                 (mapv #(add-distance1 % r) transforms))))))

(defn add-distances
  [tree-list]
  (map add-distance1 tree-list))

(defn new-machine-tx
  [ds]
  (let [machine-id (db/squuid)
        point-id   (db/squuid)]
    {:id [::id machine-id]
     :tx (concat
          [{::id           machine-id
            ::display-name "New Machine"
            ::transforms   [{::id point-id
                                   ::display-name "Origin"
                                   ::position {}}]}]
          (set-reference?-tx ds [::id point-id]))}))

;; A scale has a controller, which is what we connect to.  Multiple scales can share one.
(s/def ::controller (s/keys :req [::id
                                  ::display-name
                                  ::hardware-address
                                  ::connection-status]
                            :opt [::receive-buffer]))

;; A untranslated reading, as from a scale.
(s/def ::raw-count number?)

(defn upsert-raw-count-tx
  [ds controller-id scale-name value]
  (let [name->id (->> (d/q '[:find ?name ?id
                             :in $ ?controller
                             :where
                             [?e ::id ?id]
                             [?e ::display-name ?name]
                             [?e ::controller ?controller]]
                           ds
                           controller-id)
                      (into {}))]
    [{::id           (or (get name->id scale-name)
                         (db/squuid))
      ::display-name scale-name
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

;; Unprocess, received data
(s/def ::receive-buffer string?)

(defn add-controllers-tx
  [ds controller-list]
  {:pre [(d/db? ds)
         (s/valid? (s/coll-of (s/keys :req [::display-name ::hardware-address])) controller-list)]}
  (let [addr->controller (into {}
                               (map (juxt ::hardware-address identity))
                               (d/q '[:find [(pull ?obj [::id ::hardware-address ::connection-status]) ...]
                                      :where [?obj ::hardware-address]]
                                    ds))]
    (map (fn [{:keys [::hardware-address], :as scale-controller}]
           (let [{:keys [::id ::connection-status]} (get addr->controller hardware-address)
                 new-status                     (or connection-status :disconnected)
                 new-id                         (or id (db/squuid))]
             (assoc scale-controller
                    ::id new-id
                    ::connection-status new-status)))
         controller-list)))

(defn add-received-data-tx
  [ds controller-id data]
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
                (upsert-raw-count-tx ds controller-id scale-name value))
              new-scale-values))))

(defn store-to-reference-tx
  [ds scale-id]
  [])
