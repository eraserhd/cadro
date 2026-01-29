(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [re-frame.core :as rf]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(rf/reg-sub
 ::axes
 :<- [:session]
 (fn [session _]
   (model/axes session)))

(defn axes-panel []
  (let [axes @(rf/subscribe [::axes])]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/id
                            ::model/displays-as
                            ::model/raw-count]}]
                 [:div.axis
                  [:div.name displays-as]
                  [:div.value raw-count]
                  [:button.icon.store
                   [:> fa/FontAwesomeIcon
                    {:icon faSolid/faLocationCrosshairs}]]]))
          axes)))
