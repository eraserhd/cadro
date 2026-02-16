(ns cadro.model.scales
  (:require-macros
   [net.eraserhead.clara-eql.core :as clara-eql]
   [clara.rules :as clara])
  (:require
   [cadro.model :as model]
   [clara.rules :as clara]
   [clara-eav.eav :as eav]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [net.eraserhead.clara-eql.core :as clara-eql]
   [net.eraserhead.clara-eql.pull :as pull]))

(def schema
  [(eav/->EAV ::hardware-address :db/unique      :db.unique/identity)
   ;; Hrmm, clara-eql can't figure this out?
   (eav/->EAV ::_controller      :db/cardinality :db.cardinality/many)])

;; A scale has a controller, which is what we connect to.  Multiple scales can share one.
(s/def ::controller (s/keys :req [:cadro.model/id
                                  :cadro.model/displays-as
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
  (model/upsert session controller-id ::connection-status status))

;; Unprocessed, received data
(s/def ::receive-buffer string?)

(clara/defquery controllers []
  [eav/EAV (= e ?id) (= a :cadro.model/displays-as)       (= v ?displays-as)]
  [eav/EAV (= e ?id) (= a ::hardware-address)  (= v ?hardware-address)]
  [eav/EAV (= e ?id) (= a ::connection-status) (= v ?connection-status)])

(defn insert-controllers
  [session controller-list]
  {:pre [(s/valid? (s/coll-of (s/keys :req [:cadro.model/displays-as ::hardware-address])) controller-list)]}
  (let [existing-controllers (->> (clara/query session controllers)
                                  (group-by :?hardware-address))]
    (reduce (fn [session {displays-as :cadro.model/displays-as
                          hardware-address ::hardware-address}]
              (let [{:keys [?id ?connection-status]} (get-in existing-controllers [hardware-address 0])
                    id                               (or ?id (random-uuid))
                    connection-status                (or ?connection-status :disconnected)]
                (-> session
                    (model/upsert id :cadro.model/displays-as displays-as)
                    (model/upsert id ::hardware-address hardware-address)
                    (model/upsert id ::connection-status connection-status))))
            session
            controller-list)))

(clara/defquery scale-id [?controller-id ?scale-name]
  [eav/EAV (= e ?scale-id) (= a ::controller) (= v ?controller-id)]
  [eav/EAV (= e ?scale-id) (= a :cadro.model/displays-as) (= v ?scale-name)])

(defn upsert-raw-count [session controller-id scale-name value]
  (if-let [id (:?scale-id (first (clara/query session scale-id :?controller-id controller-id :?scale-name scale-name)))]
    (model/upsert session id ::raw-count value)
    (let [id (random-uuid)]
      (-> session
          (model/upsert id :cadro.model/displays-as scale-name)
          (model/upsert id ::raw-count value)
          (model/upsert id ::controller controller-id)))))

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
            (model/upsert session controller-id ::receive-buffer to-process)
            new-scale-values)))

(clara-eql/defrule scales-rule
  :query
  [:cadro.model/id
   :cadro.model/displays-as
   ::hardware-address
   ::connection-status
   {::_controller [:cadro.model/id
                   :cadro.model/displays-as
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
                                                 (sort-by :cadro.model/displays-as scales)))))))

(clara/defquery fixture-scales [?fixture-id]
  [eav/EAV (= e ?fixture-id) (= a ::spans) (= v ?scale-id)])
