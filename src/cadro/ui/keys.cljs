(ns cadro.ui.keys
  (:refer-clojure :exclude [keys])
  (:require
   [reagent.core :as r]
   [reagent.hooks :as rh]))

(defn keys
  "Wraps content, and globally handles keys while visible."
  [{:keys [on-keys]} form]
  (let [on-keys-ref (atom on-keys)
        on-keydown  (fn [e]
                      (when-let [handler (get @on-keys-ref (.-key e))]
                        (handler)))]
    (r/create-class
     {:reagent-render
      (fn [{:keys [on-keys], :as props} form]
        (reset! on-keys-ref on-keys)
        form)

      :component-did-mount
      (fn [this]
        (.addEventListener js/document "keydown" on-keydown))

      :component-will-unmount
      (fn [this]
        (.removeEventListener js/document "keydown" on-keydown))})))
