(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))


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

(defn axes-panel []
  (let [reference-tree @(rf/subscribe [::reference])
        axes           (model/reference-axes reference-tree)]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/display-name
                            ::model/raw-count]}]
                 [:div.axis
                  [:div.name display-name]
                  [:div.value raw-count]]))
          axes)))
