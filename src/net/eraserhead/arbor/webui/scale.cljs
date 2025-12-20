(ns net.eraserhead.arbor.webui.scale
  (:require
   [re-frame.core :as rf]
   [net.eraserhead.arbor.scale :as scale]))

(rf/reg-sub
 ::scale/devices
 (fn [{:keys [::scale/devices]} _]
   devices))

(rf/reg-event-fx ::scale/device-list-arrived scale/device-list-arrived)

(rf/reg-sub
 ::scale/log
 (fn [{:keys [::scale/log ::scale/devices]} _]
   (for [{:keys [::scale/id], :as entry} log]
     (-> entry
         (assoc ::scale/name (get-in devices [id ::scale/name]))))))

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
     (scale/log-event device-id "set-status" (str status)))))

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
