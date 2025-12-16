(ns net.eraserhead.arbor.bluetooth
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as s]))

(s/def ::id string?)
(s/def ::name string?)
(s/def ::address string?)

(s/def ::device (s/keys :req [::id ::name ::address]))
(s/def ::devices (s/map-of ::id ::device))
(s/def ::device-list (s/coll-of ::device))

(s/def ::db (s/keys :opt [::devices]))
(s/def ::effects (s/keys :req-un [::db]))

(defn device-list-arrived
  "When a new device list arrives, add new devices and remove missing devices
  from our internal device map, while keeping state of existing devices."
  [{:keys [db]} [_ device-list]]
  {:pre [(s/assert ::db db)
         (s/assert ::device-list device-list)]
   :post [(s/assert ::effects %)]}
  {:db (update db ::devices (fn [devices]
                              (let [arrived   (into #{} (map ::id) device-list)
                                    have      (into #{} (keys devices))
                                    to-remove (set/difference have arrived)
                                    updates   (->> device-list
                                                   (reduce (fn [devices {:keys [::id], :as device}]
                                                             (assoc devices id device))
                                                           {}))]
                                (as-> devices $
                                  (apply dissoc $ to-remove)
                                  (merge-with merge $ updates)
                                  (reduce-kv (fn [m id {:keys [::status],
                                                        :or {status :disconnected}
                                                        :as device}]
                                               (assoc m id (assoc device ::status status)))
                                             {}
                                             $)))))})
