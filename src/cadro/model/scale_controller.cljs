(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datascript.core :as d]))

(s/def ::scale-controller (s/keys :req [::model/id
                                        ::model/display-name
                                        ::model/hardware-address
                                        ::model/connection-status]
                                  :opt [::model/receive-buffer]))

(s/def :cadro.model/controller ::scale-controller)

(defn add-controllers-tx
  [ds controller-list]
  {:pre [(d/db? ds)
         (s/assert (s/coll-of (s/keys :req [::model/display-name ::model/hardware-address])) controller-list)]}
  (let [addr->controller (into {}
                               (map (juxt ::model/hardware-address identity))
                               (d/q '[:find [(pull ?obj [::model/id ::model/hardware-address ::model/connection-status]) ...]
                                      :where [?obj ::model/hardware-address]]
                                    ds))]
    (map (fn [{:keys [::model/hardware-address], :as scale-controller}]
           (let [{:keys [::model/id ::model/connection-status]} (get addr->controller hardware-address)
                 new-status                     (or connection-status :disconnected)
                 new-id                         (or id (db/squuid))]
             (assoc scale-controller
                    ::model/id new-id
                    ::model/connection-status new-status)))
         controller-list)))

(defn add-received-data-tx
  [ds controller-id data]
  {:pre [(d/db? ds)
         (string? data)]}
  (let [to-process                    (-> (str (::model/receive-buffer (d/entity ds controller-id))
                                               data)
                                          (str/replace #"[;\s]+" ";")
                                          (str/replace #"^;+" ""))
        [to-process new-scale-values] (loop [to-process       to-process
                                             new-scale-values {}]
                                        (if-let [[_ axis value-str left] (re-matches #"^([a-zA-Z])(-?\d+(?:\.\d*)?);(.*)" to-process)]
                                          (recur left (assoc new-scale-values axis (* value-str 1.0)))
                                          [to-process new-scale-values]))]
    (concat
      [[:db/add controller-id ::model/receive-buffer to-process]]
      (mapcat (fn [[scale-name value]]
                (model/upsert-raw-count-tx ds controller-id scale-name value))
              new-scale-values))))
