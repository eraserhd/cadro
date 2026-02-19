(ns cadro.bluetooth
  (:require
   [cadro.model :as model]
   [cadro.model.facts :as facts]
   [cadro.model.scales :as scales]
   [cadro.session :as session]
   [clara.rules :as clara]
   [re-frame.core :as rf]
   ["@capacitor/core" :refer [Capacitor]]
   ["cordova-plugin-bluetooth-classic-serial-port/src/browser/bluetoothClassicSerial" :as bt-browser]))

(def ^:private interface-id "00001101-0000-1000-8000-00805f9b34fb")

(def ^:private ^:dynamic mock-data
  {"X" 500
   "Y" 500
   "Z" 500})

(set! (.-subscribeRawData bt-browser)
      (fn [device-id interface-id success failure]
        (let [encoder (js/TextEncoder. "ascii")]
          (js/window.setInterval
           (fn []
             (let [data (.encode encoder
                                 (str (->> mock-data
                                           (map (fn [[axis value]]
                                                  (str axis value ";")))
                                           (apply str))
                                      "\n"))]
               (success data)))
           50))))

(def ^:private bt-impl
  (if (= "web" (.getPlatform Capacitor))
    (do
      (js/console.log "using fake fallback bluetooth serial implementation")
      bt-browser)
    (do
      (js/console.log "found Cordova bluetooth serial implementation")
      js/bluetoothClassicSerial)))

(defmethod session/start-hook :bluetooth
 [session]
 (js/console.log "bluetooth: resetting connection states after session reload")
 (let [connected-ids (scales/connected-controller-ids session)]
   (js/console.log "bluetooth: found" (count connected-ids) "previously connected controllers")
   ;; Reset all to disconnected and mark for reconnection
   (reduce (fn [session id]
             (-> session
                 (facts/upsert id ::scales/connection-status :disconnected)
                 (clara/insert (facts/derived id ::scales/wants-reconnect? true))))
           session
           connected-ids)))

(rf/reg-event-fx
 ::device-list-arrived
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-list]]
   {:session        (scales/insert-controllers session device-list)
    :dispatch-later [{:ms 100 :dispatch [::attempt-reconnections]}]}))

(rf/reg-event-fx
 ::attempt-reconnections
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} _]
   (let [to-reconnect (clara/query session scales/controllers-to-reconnect)
         controller-ids (map :?id to-reconnect)]
     (js/console.log "bluetooth: attempting to reconnect" (count controller-ids) "controllers")
     (if (seq controller-ids)
       ;; Clean up the wants-reconnect? markers
       (let [cleanup-session (reduce (fn [s id]
                                       (let [facts-to-retract (map :?fact (clara/query s scales/wants-reconnect-facts :?controller-id id))]
                                         (apply clara/retract s facts-to-retract)))
                                     session
                                     controller-ids)]
         {:session    cleanup-session
          :dispatch-n (map (fn [id] [::connect id]) controller-ids)})
       {}))))

(rf/reg-fx
 ::fetch-device-list
 (fn fetch-device-list* []
   (.list bt-impl
          (fn [devices]
            (let [device-list (into []
                                    (map (fn [device]
                                           {::model/displays-as       (.-name device)
                                            ::scales/hardware-address (.-address device)}))
                                    devices)]
              (rf/dispatch [::device-list-arrived device-list])))
          (fn [error]
            (js/alert (str "Unable to retrieve Bluetooth device list: " error))))))

(rf/reg-event-fx
 ::connect-requested
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id]]
   (js/console.info "bluetooth:" (str device-id) "connect requested")
   {:session (scales/set-connection-status session device-id :connecting)}))

(rf/reg-event-fx
 ::connect-completed
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id]]
   (js/console.info "bluetooth:" (str device-id) "connect completed")
   {:session (scales/set-connection-status session device-id :connected)}))

(rf/reg-event-fx
 ::connect-failed
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id error]]
   (js/console.info "bluetooth:" (str device-id) "connect failed:" error)
   (js/console.error "bluetooth:" (str device-id) "connect failed:" error)
   {:session (scales/set-connection-status session device-id :disconnected)}))

(defn hex-dump [s]
  (->> (.encode (js/TextEncoder.) s)
       (partition-all 16)
       (map-indexed
         (fn [line-num chunk]
           (let [hex-parts     (map #(.padStart (.toString % 16) 2 "0") chunk)
                 ascii-parts   (map (fn [b]
                                      (if (and (>= b 32) (<= b 126))
                                        (js/String.fromCharCode b)
                                        "."))
                                    chunk)
                 padding       (apply str (repeat (- 16 (count chunk)) "   "))
                 hex-section   (str (clojure.string/join " " hex-parts) padding)
                 ascii-section (clojure.string/join "" ascii-parts)
                 offset-str    (.padStart (.toString (* line-num 16) 16) 4 "0")]
             (str offset-str ": " hex-section "  | " ascii-section))))
       (clojure.string/join "\n")))

(rf/reg-event-fx
 ::data-received
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id data]]
   ;; Disable expensive hex-dump logging for performance
   ;; (js/console.info "bluetooth:" (str device-id) "data received:\n" (hex-dump data))
   (let [start (.now js/performance)
         result {:session (scales/add-received-data session device-id data)}
         duration (- (.now js/performance) start)]
     (when (> duration 40)
       (js/console.warn "Slow packet processing:" (.toFixed duration 2) "ms"))
     result)))

(rf/reg-event-fx
 ::subscription-error-received
 (fn [_ [_ device-id error]]
   (js/console.info "bluetooth:" (str device-id) "subscription error:" error)
   (js/console.error "bluetooth:" (str device-id) "subscription error:" error)
   {}))

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::connect
 (fn connect* [device-id]
   (rf/dispatch [::connect-requested device-id])
   (let [device-address (->> (clara/query @cadro.session/session scales/controllers)
                             (filter (fn [{:keys [?id]}]
                                       (= ?id device-id)))
                             (map :?hardware-address)
                             first)]
     (.connect
      bt-impl
      device-address
      interface-id
      (fn []
        (rf/dispatch [::connect-completed device-id])
        (.subscribeRawData
         bt-impl
         device-address
         interface-id
         (fn [raw-data]
           (let [data (.decode decoder (js/Uint8Array. raw-data))]
             (rf/dispatch [::data-received device-id data])))
         (fn [error]
           (rf/dispatch [::subscription-error-received device-id error]))))
      (fn [error]
        (rf/dispatch [::connect-failed device-id error]))))))

(defn bt-status []
  (println (str "Currently receiving: " mock-data)))

(defn bt-set [axis-name value]
  (set! mock-data (assoc mock-data axis-name value))
  (bt-status))
