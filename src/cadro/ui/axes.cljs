(ns cadro.ui.axes
  (:require
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.model.scale :as scale]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(re-posh/reg-sub
 ::reference
 (fn [ds _]
   {:type    :pull
    :pattern '[{::locus/reference
                [{::model/spans
                  [::model/display-name
                   ::scale/raw-value]}]}]
    :id      ::locus/global}))

(defn axes-panel []
  (let [reference (::locus/reference @(rf/subscribe [::reference]))
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
