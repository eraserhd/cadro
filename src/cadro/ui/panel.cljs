(ns cadro.ui.panel
  (:require
   [cadro.ui.gestures :as gestures]
   [cadro.ui.keys]
   [reagent.core :as r]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(def close-icon [:> fa/FontAwesomeIcon {:icon faSolid/faXmark}])

(defn panel
  [{:keys [title on-close class]} & content]
  [cadro.ui.keys/keys {:on-keys {"Escape" on-close}}
   [gestures/wrap {:on-swipedown (fn []
                                   (when-let [active-element js/document.activeElement]
                                     (.blur active-element))
                                   (on-close))}
    [:div.floating-card {:class class}
     [:div.header
      [:h1 title]
      [:button.close
       {:type "button",
        :on-click on-close}
       close-icon]]
     (into [:form {:on-submit #(.preventDefault %)}]
           content)]]])
