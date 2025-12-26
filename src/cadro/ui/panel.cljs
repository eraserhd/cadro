(ns cadro.ui.panel
  (:require
   [cadro.ui.gestures :as gestures]
   [cadro.ui.keys]
   [reagent.core :as r]))

(def close-icon [:i.fa-solid.fa-xmark])

(defn panel
  [{:keys [title on-close class]} & content]
  [cadro.ui.keys/keys {:on-keys {"Escape" on-close}}
   [gestures/wrap {:on-swipedown on-close}
    [:div.floating-card {:class class}
     [:div.header
      [:h1 title]
      [:button.close
       {:on-click on-close}
       close-icon]]
     (into [:form] content)]]])
