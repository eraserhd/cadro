(ns cadro.bluetooth
  (:require
   [cadro.model.object :as object]
   [cadro.model.scale-controller :as scale-controller]
   [datascript.core :as d]
   [re-posh.core :as re-posh]
   [re-frame.core :as rf]
   ["cordova-plugin-bluetooth-classic-serial-port/src/browser/bluetoothClassicSerial" :as bt-browser]))

(def ^:private interface-id "00001101-0000-1000-8000-00805f9b34fb")

(def ^:private ^:dynamic fake-subscribeRawData-reply "X500;Y500;Z500;\n")
(def ^:private bt-impl
  (atom
   (let [fake-impl bt-browser]
     ;; Browser fake is not plumbed correctly for subscribeRawData
     (set! (.-subscribeRawData fake-impl)
           (fn [device-id interface-id success failure]
             (let [encoder (js/TextEncoder. "ascii")]
               (js/window.setInterval
                (fn []
                  (let [data (.encode encoder fake-subscribeRawData-reply)]
                    (success data)))
                500))))
     fake-impl)))

(.addEventListener js/document "deviceready"
  (fn []
    (if-let [impl js/bluetoothClassicSerial]
      (do
        (js/console.log "found cordova bluetooth serial implementation")
        (reset! bt-impl js/bluetoothClassicSerial))
      (js/console.log "using fallback (fake) bluetooth serial implementation"))))

(re-posh/reg-event-ds
 ::device-list-arrived
 (fn [ds [_ device-list]]
   (let [existing (->> (d/q '[:find [?addr ...]
                              :where
                              [?obj ::scale-controller/address ?addr]
                              [?obj ::scale-controller/status ?status]]
                            ds)
                      (into #{}))]
     (->> device-list
          (map (fn [{:keys [::scale-controller/address], :as scale-controller}]
                 (cond-> scale-controller
                   (not (contains? existing address)) (assoc ::scale-controller/status :disconnected))))))))

(rf/reg-fx
 ::fetch-device-list
 (fn fetch-device-list* []
   (.list @bt-impl
          (fn [devices]
            (let [device-list (into []
                                    (map (fn [device]
                                           {::object/id                (.-id device)
                                            ::object/display-name      (.-name device)
                                            ::scale-controller/address (.-address device)}))
                                    devices)]
              (rf/dispatch [::device-list-arrived device-list])))
          (fn [error]
            (js/alert (str "Unable to retrieve Bluetooth device list: " error))))))

(rf/reg-event-fx
 ::fetch-device-list
 (fn [_ _]
   {::fetch-device-list nil}))

(re-posh/reg-event-ds
 ::connect-requested
 (fn [ds [_ device-id]]
   (scale-controller/set-status-tx device-id :connecting)))

(re-posh/reg-event-ds
 ::connect-completed
 (fn [ds [_ device-id]]
   (scale-controller/set-status-tx device-id :connected)))

(re-posh/reg-event-ds
 ::connect-failed
 (fn [ds [_ device-id]]
  ;   (rf/dispatch [::scale-controller/log-event device-id "connect error" error])
  ;   (js/alert (str "Unable to connect: " error))]
   (scale-controller/set-status-tx device-id :disconnected)))

(re-posh/reg-event-ds
 ::data-received
 (fn [ds [_ device-id data]]
   (scale-controller/data-received-tx ds device-id data)))

(re-posh/reg-event-ds
 ::subscription-error-received
 (fn [ds [_ device-id error]]
   ;     (rf/dispatch [::scale-controller/log-event device-id "subscribeRawData error" error])]
   ;FIXME:
   []))

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::connect
 (fn connect* [device-id]
   (rf/dispatch [::connect-requested device-id])
   (.connect
    @bt-impl
    device-id
    interface-id
    (fn []
      (rf/dispatch [::connect-completed device-id])
      (.subscribeRawData
       @bt-impl
       device-id
       interface-id
       (fn [raw-data]
         (let [data (.decode decoder (js/Uint8Array. raw-data))]
           (rf/dispatch [::data-received device-id data])))
       (fn [error]
         (rf/dispatch [::subscription-error-received device-id error]))))
    (fn [error]
      (rf/dispatch [::connect-failed device-id error])))))
