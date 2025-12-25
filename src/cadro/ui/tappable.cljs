(ns cadro.ui.tappable
  (:require
   [reagent.core :as r]
   ["hammerjs" :as Hammer]))

(defn tappable
  [{:keys [on-tap on-press]} & args]
  (let [button-ref (atom nil)]
    (r/create-class
      {:reagent-render
       (fn []
         (into [:button {:ref #(reset! button-ref %)}]
               args))

       :component-did-mount
       (fn [this]
         (when-let [button @button-ref]
           (prn :settingup)
           (let [hammer (Hammer/Manager. button)]
             (r/set-state this {:hammer hammer})
             (when on-tap
               (.add hammer (Hammer/Tap. #js {}))
               (.on hammer "tap" on-tap))
             (when on-press
               (.add hammer (Hammer/Press. #js {}))
               (.on hammer "press" on-press)))))

       :component-will-unmount
       (fn [this]
         (when-let [{:keys [hammer]} (r/state this)]
           (prn :destroy)
           (.destroy hammer)))})))
