(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.model.scale :as scale]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))


(re-posh/reg-sub
 ::reference-id
 (fn [ds _]
   {:type  :query
    :query '[:find ?eid .
             :where
             [?eid ::model/reference? true]]}))

(re-posh/reg-sub
 ::reference
 :<- [::reference-id]
 (fn [reference-id _]
   {:type    :pull
    :pattern '[{::model/spans
                [::model/display-name
                 ::scale/raw-value]}]
    :id      reference-id}))

(defn axes-panel []
  (let [reference @(rf/subscribe [::reference])
        axes      (->> (::model/spans reference)
                       (sort-by ::model/display-name))]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/display-name
                            ::scale/raw-value]}]
                 [:div.axis
                  [:div.name display-name]
                  [:div.value raw-value]]))
          axes)))
