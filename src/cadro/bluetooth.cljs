(ns cadro.bluetooth
  (:require
   [cadro.model.object :as object]
   [cadro.model.scale-controller :as scale-controller]
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
   device-list))

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

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::connect
 (fn connect* [device-id]
   (rf/dispatch [::scale-controller/set-status device-id :connecting])
   (.connect
    @bt-impl
    device-id
    interface-id
    (fn []
      (rf/dispatch [::scale-controller/set-status device-id :connected])
      (.subscribeRawData
       @bt-impl
       device-id
       interface-id
       (fn [raw-data]
         (let [data (.decode decoder (js/Uint8Array. raw-data))]
           (rf/dispatch [::scale-controller/process-received device-id data])))
       (fn [error]
         (rf/dispatch [::scale-controller/log-event device-id "subscribeRawData error" error]))))
    (fn [error]
      (rf/dispatch [::scale-controller/log-event device-id "connect error" error])
      (rf/dispatch [::scale-controller/set-status device-id :disconnected])
      (js/alert (str "Unable to connect: " error))))))

(rf/reg-event-fx
 ::connect
 (fn [_ [_ device-id]]
   {::connect device-id}))
