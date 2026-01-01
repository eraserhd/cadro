(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(re-posh/reg-sub
 ::reference-id
 (fn [ds _]
   {:type  :query
    :query model/reference-id-q}))

(re-posh/reg-sub
 ::reference-tree
 :<- [::reference-id]
 (fn [reference-id _]
   {:type    :pull
    :pattern model/root-path-pull
    :id      reference-id}))

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
