(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [cadro.ui.position :as pos]
   [re-frame.core :as rf]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(rf/reg-sub
 ::axes
 :<- [:session]
 (fn [session _]
   (model/axes session)))

(rf/reg-event-fx
 ::store-clicked
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ scale-id]]
   {:session (model/store-scale-to-reference session scale-id)}))

(defn axes-panel []
  (let [axes @(rf/subscribe [::axes])]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/id
                            ::model/displays-as
                            ::model/transformed-count],
                     :as scale}]
                 [:div.axis
                  (pos/axis-hiccup displays-as transformed-count)
                  [:button.icon.store
                   {:on-click #(rf/dispatch [::store-clicked id])}
                   [:> fa/FontAwesomeIcon
                    {:icon faSolid/faLocationCrosshairs}]]]))
          axes)))
