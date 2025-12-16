(ns net.eraserhead.arbor.bluetooth
  (:require
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
  [{{:keys [::devices], :as db}, :db} [_ device-list]]
  {:pre [(s/assert ::db db)
         (s/assert ::device-list device-list)]
   :post [(s/assert ::effects %)]}
  (let [new-ids (into #{} (map ::id device-list))
        devices (->> devices
                     (remove (fn [[id _]]
                               (not (contains? new-ids id))))
                     (into {}))
        devices (->> device-list
                     (filter (comp new-ids ::id))
                     (reduce (fn [devices {:keys [::id], :as new-device}]
                               (assoc devices id (assoc new-device ::status :disconnected)))
                             devices))]
    {:db (assoc db ::devices devices)}))
