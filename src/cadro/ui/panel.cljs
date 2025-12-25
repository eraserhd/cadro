(ns cadro.ui.panel
  (:require
   [reagent.core :as r]
   ["hammerjs" :as hammer]))

(def close-icon [:i.fa-solid.fa-xmark])

(defn panel
  [{:keys [title on-close class]} & content]
  (let [on-keydown (fn [e]
                     (when (= "Escape" (.-key e))
                       (on-close)))]
    (r/create-class
     {:component-did-mount
      #(.addEventListener js/document "keydown" on-keydown)

      :component-will-unmount
       #(.removeEventListener js/document "keydown" on-keydown)

      :reagent-render
      (fn []
       [:div.floating-card {:class class}
        [:div.header
         [:h1 title]
         [:button.close
          {:on-click #(on-close)}
          close-icon]]
        (into [:form] content)])})))
