(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
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

(s/def ::scale-controller (s/keys :req [::object/id
                                        ::object/display-name
                                        ::address
                                        ::status]
                                  :opt [::receive-buffer]))

(defn set-status-tx
  [controller-id status]
  {:pre [(s/assert ::status status)]}
  [[:db/add controller-id ::status status]])

(defn add-controllers-tx
  [ds controller-list]
  {:pre [(d/db? ds)
         (s/assert (s/coll-of (s/keys :req [::object/display-name ::address])) controller-list)]}
  (let [addr->controller (into {}
                               (map (juxt ::address identity))
                               (d/q '[:find [(pull ?obj [::object/id ::address ::status]) ...]
                                      :where [?obj ::address]]
                                    ds))]
    (map (fn [{:keys [::address], :as scale-controller}]
           (let [{:keys [::object/id ::status]} (get addr->controller address)
                 new-status                     (or status :disconnected)
                 new-id                         (or id (db/squuid))]
             (assoc scale-controller
                    ::object/id new-id
                    ::status    new-status)))
         controller-list)))

(defn add-received-data-tx
  [ds controller-id data]
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
                (scale/upsert-scale-value-tx ds controller-id scale-name value))
              new-scale-values))))

;(s/def ::event-type string?)
;(s/def ::event-data string?)
;(s/def ::log-event (s/keys :req [::id ::event-type ::event-data]))
;(s/def ::log (s/coll-of ::log-event))

;(def ^:private max-log-size 100)
;(defn- append-log
;  [log event]
;  (let [log (conj (or log []) event)]
;    (if (< max-log-size (count log))
;      (into [] (drop (- (count log) max-log-size) log))
;      log)))
;(defn log-event
;  "Append a log to the device log, removing older events as necessary."
;  [db device-id event-type event-data]
;  (let [event {::id device-id
;               ::event-type event-type
;               ::event-data event-data}]
;    (update db ::log append-log event)))

;(defn- char-code [s]
;  (.charCodeAt s 0))
;(defn- pad [s n]
;  (apply str (concat s (repeat (- n (count s)) \space))))
;(defn- hex-char [i]
;  (get "0123456789abcdef" i))
;(defn- hex-byte [i]
;  (str (hex-char (quot i 16))
;       (hex-char (mod i 16))))
;(defn- format-hex [s]
;  (->> s
;       (partition-all 16)
;       (map (fn [cs]
;              (let [hex-part  (->> cs
;                                   (map char-code)
;                                   (map hex-byte)
;                                   (str/join " "))
;                    text-part (->> cs
;                                   (map (fn [ch]
;                                          (if (<= 0x20 (char-code ch) 0x7e)
;                                            ch
;                                            ".")))
;                                   (apply str))]
;                (str (pad hex-part (dec (* 16 3))) "   " text-part))))
;       (str/join "\n")))
;
;(defn- log-received
;  "Log received data in hex format."
;  [db device-id event-data]
;  (log-event db device-id "received" (format-hex event-data)))
