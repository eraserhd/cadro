(ns net.eraserhead.arbor.webui.bluetooth
  (:require
   [net.eraserhead.arbor.bluetooth :as bt]
   [re-frame.core :as rf]
   ["cordova-plugin-bluetooth-classic-serial-port/src/browser/bluetoothClassicSerial" :as bt-browser]))

(def ^:private interface-id "00001101-0000-1000-8000-00805f9b34fb")

(def ^:private bt-impl (atom bt-browser))

(.addEventListener js/document "deviceready"
  (fn []
    (if-let [impl js/bluetoothClassicSerial]
      (do
        (js/console.log "found cordova bluetooth serial implementation")
        (reset! bt-impl js/bluetoothClassicSerial))
      (js/console.log "using fallback (fake) bluetooth serial implementation"))))

(rf/reg-sub
 ::bt/devices
 (fn [{:keys [::bt/devices]} _]
   devices))

(rf/reg-event-fx ::bt/device-list-arrived bt/device-list-arrived)

(rf/reg-fx
 ::bt/fetch-device-list
 (fn fetch-device-list* []
   (.list @bt-impl
          (fn [devices]
            (let [device-list (into []
                                    (map (fn [device]
                                           {::bt/id      (.-id device)
                                            ::bt/name    (.-name device)
                                            ::bt/address (.-address device)}))
                                    devices)]
              (rf/dispatch [::bt/device-list-arrived device-list])))
          (fn [error]
            (js/alert (str "Unable to retrieve Bluetooth device list: " error))))))

(rf/reg-event-fx
 ::bt/fetch-device-list
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(rf/reg-event-db
 ::bt/log-event
 (fn [db [_ device-id event-type data]]
   (bt/log-event db device-id event-type data)))

(rf/reg-event-db
 ::bt/log-received
 (fn [db [_ device-id data]]
   (bt/log-received db device-id data)))

(rf/reg-event-db
 ::bt/set-status
 (fn [db [_ device-id status]]
   (-> db
     (assoc-in [::bt/devices device-id ::bt/status] status)
     (bt/log-event device-id "set-status" status))))

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::bt/connect
 (fn connect* [device-id]
   (rf/dispatch [::bt/set-status device-id :connecting])
   (.connect
    @bt-impl
    device-id
    interface-id
    (fn []
      (rf/dispatch [::bt/set-status device-id :connected])
      (.subscribeRawData
       @bt-impl
       device-id
       interface-id
       (fn [raw-data]
         (let [data (.decode decoder (js/Uint8Array. raw-data))]
           (rf/dispatch [::bt/log-received device-id data])))
       (fn [error]
         (rf/dispatch [::bt/log-event device-id "subscribeRawData error" error]))))
    (fn [error]
      (rf/dispatch [::bt/log-event device-id "connect error" error])
      (rf/dispatch [::bt/set-status device-id :disconnected])
      (js/alert (str "Unable to connect: " error))))))

(rf/reg-event-fx
 ::bt/connect
 (fn [_ [_ device-id]]
   {::bt/connect device-id}))
