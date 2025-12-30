(ns cadro.ui.axes
  (:require
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(re-posh/reg-sub
 ::reference
 (fn [ds _]
   {:type    :pull
    :pattern '[{::locus/reference
                [{::locus/scale-assoc
                  [{::scale/scale
                    [::object/display-name
                     ::scale/raw-value]}]}]}]
    :id      ::locus/global}))

(defn axes-panel []
  (let [reference (::locus/reference @(rf/subscribe [::reference]))
        axes      (->> (::locus/scale-assoc reference)
                       (map ::scale/scale)
                       (sort-by ::object/display-name))]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::object/display-name
                            ::scale/raw-value]}]
                 [:div.axis
                  [:div.name display-name]
                  [:div.value raw-value]]))
          axes)))
