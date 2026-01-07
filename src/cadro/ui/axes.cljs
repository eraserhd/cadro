(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.session :as session]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(rf/reg-sub
 ::reference-uuid
 (fn [_ _]
   (model/reference @session/session)))

(re-posh/reg-sub
 ::reference-tree
 :<- [::reference-uuid]
 (fn [reference-uuid _]
   {:type    :pull
    :pattern model/root-path-pull
    :id      [::model/id reference-uuid]}))

(re-posh/reg-event-ds
 ::store-to-reference-clicked
 (fn [ds [_ scale-id]]
   (model/store-to-reference-tx ds [::model/id scale-id])))

(defn axes-panel []
  (let [reference-tree @(rf/subscribe [::reference-tree])
        axes           (model/root-path-axes reference-tree)]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/id
                            ::model/display-name
                            ::model/raw-count]}]
                 [:div.axis
                  [:div.name display-name]
                  [:div.value raw-count]
                  [:button.icon.store
                   [:> fa/FontAwesomeIcon
                    {:icon faSolid/faLocationCrosshairs
                     :on-click #(rf/dispatch [::store-to-reference-clicked id])}]]]))
          axes)))
