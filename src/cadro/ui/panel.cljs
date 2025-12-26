(ns cadro.ui.panel
  (:require
   [cadro.ui.gestures :as gestures]
   [reagent.core :as r]))

(def close-icon [:i.fa-solid.fa-xmark])

(defn panel
  [{:keys [title on-close class]} & content]
  (let [on-keydown (fn [e]
                     (when (= "Escape" (.-key e))
                       (on-close)))]
    (r/create-class
     {:reagent-render
      (fn [{:keys [title on-close class]} & content]
        [hammer/wrap {:on-swipedown on-close}
         [:div.floating-card {:class class}
          [:div.header
           [:h1 title]
           [:button.close
            {:on-click on-close}
            close-icon]]
          (into [:form] content)]])

      :component-did-mount
      (fn [this]
        (.addEventListener js/document "keydown" on-keydown))

      :component-will-unmount
      (fn [this]
        (.removeEventListener js/document "keydown" on-keydown))})))
