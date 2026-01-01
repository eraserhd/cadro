(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datascript.core :as d]))

(db/register-schema!
 {::address {:db/cardinality :db.cardinality/one
             :db/unique      :db.unique/identity}
  ::status  {:db/cardinality :db.cardinality/one}})

(s/def ::address string?)
(s/def ::status #{:disconnected
                  :connecting
                  :connected})
(s/def ::receive-buffer string?)

(s/def ::scale-controller (s/keys :req [::model/id
                                        ::model/display-name
                                        ::address
                                        ::status]
                                  :opt [::receive-buffer]))

(s/def :cadro.model/controller ::scale-controller)

(defn address
  [ds device-id]
  (::address (d/entity ds device-id)))

(defn set-status-tx
  [controller-id status]
  {:pre [(s/assert ::status status)]}
  [[:db/add controller-id ::status status]])

(defn add-controllers-tx
  [ds controller-list]
  {:pre [(d/db? ds)
         (s/assert (s/coll-of (s/keys :req [::model/display-name ::address])) controller-list)]}
  (let [addr->controller (into {}
                               (map (juxt ::address identity))
                               (d/q '[:find [(pull ?obj [::model/id ::address ::status]) ...]
                                      :where [?obj ::address]]
                                    ds))]
    (map (fn [{:keys [::address], :as scale-controller}]
           (let [{:keys [::model/id ::status]} (get addr->controller address)
                 new-status                     (or status :disconnected)
                 new-id                         (or id (db/squuid))]
             (assoc scale-controller
                    ::model/id new-id
                    ::status    new-status)))
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
                (model/upsert-raw-count-tx ds controller-id scale-name value))
              new-scale-values))))
