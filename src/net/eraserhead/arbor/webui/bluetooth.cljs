(ns net.eraserhead.arbor.webui.bluetooth
  (:require
   [net.eraserhead.arbor.scale :as scale]
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

(rf/reg-sub
 ::scale/devices
 (fn [{:keys [::scale/devices]} _]
   devices))

(rf/reg-event-fx ::scale/device-list-arrived scale/device-list-arrived)

(rf/reg-fx
 ::fetch-device-list
 (fn fetch-device-list* []
   (.list @bt-impl
          (fn [devices]
            (let [device-list (into []
                                    (map (fn [device]
                                           {::scale/id      (.-id device)
                                            ::scale/name    (.-name device)
                                            ::scale/address (.-address device)}))
                                    devices)]
              (rf/dispatch [::scale/device-list-arrived device-list])))
          (fn [error]
            (js/alert (str "Unable to retrieve Bluetooth device list: " error))))))

(rf/reg-event-fx
 ::fetch-device-list
 (fn [_ _]
   {::fetch-device-list nil}))

(rf/reg-sub
 ::scale/log
 (fn [{:keys [::scale/log ::scale/devices]} _]
   (for [{:keys [::scale/id], :as entry} log]
     (-> entry
         (assoc ::scale/name (get-in devices [id ::scale/name]))))))

(def device-log-icon [:i.fa-solid.fa-ruler-combined])

(defn device-log []
  [:div.floating-card.device-log
   [:h1 device-log-icon " Device Log"]
   [:div.log-scroll
    [:table
     [:thead
       [:tr [:th "Device"] [:th "Event"] [:th "Data"]]]
     (into [:tbody]
           (map (fn [{:keys [::scale/name ::scale/event-type ::scale/event-data]}]
                  [:tr [:td name] [:td event-type] [:td [:pre event-data]]]))
           @(rf/subscribe [::scale/log]))]]])

(rf/reg-event-db
 ::scale/log-event
 (fn [db [_ device-id event-type data]]
   (scale/log-event db device-id event-type data)))

(rf/reg-event-db
 ::scale/process-received
 (fn [db [_ device-id data]]
   (scale/process-received db device-id data)))

(rf/reg-event-db
 ::scale/set-status
 (fn [db [_ device-id status]]
   (-> db
     (assoc-in [::scale/devices device-id ::scale/status] status)
     (scale/log-event device-id "set-status" status))))

(def ^:private decoder (js/TextDecoder. "ascii"))

(rf/reg-fx
 ::connect
 (fn connect* [device-id]
   (rf/dispatch [::scale/set-status device-id :connecting])
   (.connect
    @bt-impl
    device-id
    interface-id
    (fn []
      (rf/dispatch [::scale/set-status device-id :connected])
      (.subscribeRawData
       @bt-impl
       device-id
       interface-id
       (fn [raw-data]
         (let [data (.decode decoder (js/Uint8Array. raw-data))]
           (rf/dispatch [::scale/process-received device-id data])))
       (fn [error]
         (rf/dispatch [::scale/log-event device-id "subscribeRawData error" error]))))
    (fn [error]
      (rf/dispatch [::scale/log-event device-id "connect error" error])
      (rf/dispatch [::scale/set-status device-id :disconnected])
      (js/alert (str "Unable to connect: " error))))))

(rf/reg-event-fx
 ::connect
 (fn [_ [_ device-id]]
   {::connect device-id}))
