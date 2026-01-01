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
    :query model/reference?-id-q}))

(re-posh/reg-sub
 ::reference
 :<- [::reference-id]
 (fn [reference-id _]
   {:type    :pull
    :pattern '[::model/position
               {::model/_transforms ...
                ::model/spans
                [::model/display-name
                 ::model/raw-count]}]
    :id      reference-id}))

(defn axes-panel []
  (let [reference @(rf/subscribe [::reference])
        axes      (->> (iterate (comp first ::model/_transforms) reference)
                       (take-while some?)
                       (mapcat ::model/spans)
                       (sort-by ::model/display-name))]
    (into [:div.floating-card.axes
           [:h1 "Axes"]]
          (map (fn [{:keys [::model/display-name
                            ::model/raw-count]}]
                 [:div.axis
                  [:div.name display-name]
                  [:div.value raw-count]]))
          axes)))
