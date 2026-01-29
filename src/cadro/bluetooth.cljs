(ns cadro.bluetooth
  (:require
   [cadro.model :as model]
   [clara.rules :as clara]
   [cadro.session :as session]
   [re-frame.core :as rf]
   ["@capacitor/core" :refer [Capacitor]]
   ["cordova-plugin-bluetooth-classic-serial-port/src/browser/bluetoothClassicSerial" :as bt-browser]))

(def ^:private interface-id "00001101-0000-1000-8000-00805f9b34fb")

(def ^:private ^:dynamic fake-subscribeRawData-reply "X500;Y500;Z500;\n")
(set! (.-subscribeRawData bt-browser)
      (fn [device-id interface-id success failure]
        (let [encoder (js/TextEncoder. "ascii")]
          (js/window.setInterval
           (fn []
             (let [data (.encode encoder fake-subscribeRawData-reply)]
               (success data)))
           500))))

(def ^:private bt-impl
  (if (= "web" (.getPlatform Capacitor))
    (do
      (js/console.log "using fake fallback bluetooth serial implementation")
      bt-browser)
    (do
      (js/console.log "found Cordova bluetooth serial implementation")
      js/bluetoothClassicSerial)))

(rf/reg-event-fx
 ::device-list-arrived
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-list]]
   (let [uuids [(random-uuid) (random-uuid) (random-uuid) (random-uuid) (random-uuid) (random-uuid)]]
     {:session  (model/insert-controllers session device-list (atom uuids))})))

(rf/reg-fx
 ::fetch-device-list
 (fn fetch-device-list* []
   (.list bt-impl
          (fn [devices]
            (let [device-list (into []
                                    (map (fn [device]
                                           {::model/displays-as       (.-name device)
                                            ::model/hardware-address (.-address device)}))
                                    devices)]
              (rf/dispatch [::device-list-arrived device-list])))
          (fn [error]
            (js/alert (str "Unable to retrieve Bluetooth device list: " error))))))

(rf/reg-event-fx
 ::connect-requested
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id]]
   {:session (model/set-connection-status session device-id :connecting)}))

(rf/reg-event-fx
 ::connect-completed
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id]]
   {:session (model/set-connection-status session device-id :connected)}))

(rf/reg-event-fx
 ::connect-failed
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id error]]
  ;   (rf/dispatch [::scale-controller/log-event device-id "connect error" error])
  ;   (js/alert (str "Unable to connect: " error))]
   {:session (model/set-connection-status session device-id :disconnected)}))

(rf/reg-event-fx
 ::data-received
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ device-id data]]
   (let [uuids [(random-uuid) (random-uuid) (random-uuid) (random-uuid) (random-uuid) (random-uuid)]]
     {:session (model/add-received-data session device-id data (atom uuids))})))

(rf/reg-event-fx
 ::subscription-error-received
 [(rf/inject-cofx :session)]
 (fn [{:keys [ds]} [_ device-id error]]
   ;     (rf/dispatch [::scale-controller/log-event device-id "subscribeRawData error" error])]
   ;FIXME:
   {}))

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::connect
 (fn connect* [device-id]
   (rf/dispatch [::connect-requested device-id])
   (let [device-address (->> (clara/query @cadro.session/session model/controllers)
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
        (rf/dispatch [::connect-failed device-id error])
        (js/alert (str "Unable to connect: " error)))))))
