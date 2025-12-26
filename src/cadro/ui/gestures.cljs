(ns cadro.ui.gestures
  (:require
   [reagent.core :as r]
   ["hammerjs" :as Hammer]))

(defn- make-hammer
  [e {:keys [on-tap on-press on-swipedown]}]
  (let [hammer (Hammer/Manager. e)]
    (when on-tap
      (.add hammer (Hammer/Tap. #js {}))
      (.on hammer "tap" on-tap))
    (when on-press
      (.add hammer (Hammer/Press. #js {}))
      (.on hammer "press" on-press))
    (when on-swipedown
      (.add hammer (Hammer/Swipe. #js {:direction Hammer/DIRECTION_VERTICAL}))
      (.on hammer "swipedown" on-swipedown))
    hammer))

(defn wrap
  "Wrap a component with Hammerjs event handling.

  props can contain:

    :on-tap
    :on-press
    :on-swipedown
  "
  [props [wrapped-type & wrapped-args]]
  (let [node-ref   (atom nil)
        props-ref  (atom props)
        hammer-ref (atom nil)]
    (r/create-class
     {:reagent-render
      (fn [props [wrapped-type & wrapped-args]]
        (reset! props-ref props)
        (let [[wrapped-props & wrapped-args] (if (map? (first wrapped-args))
                                               wrapped-args
                                               (cons {} wrapped-args))
              wrapped-props (merge wrapped-props
                                   {:ref #(reset! node-ref %)})]
          (into [wrapped-type wrapped-props] wrapped-args)))

      :component-did-mount
      (fn [this]
        (when-let [node @node-ref]
          (when-let [props @props-ref]
            (let [hammer (make-hammer node props)]
              (r/set-state this {:hammer hammer})))))

      :component-did-update
      (fn [this]
        (when-let [hammer (:hammer (r/state this))]
          (.destroy hammer))
        (when-let [node @node-ref]
          (when-let [props @props-ref]
            (let [hammer (make-hammer node props)]
              (r/set-state this {:hammer hammer})))))

      :component-will-unmount
      (fn [this]
        (when-let [hammer (:hammer (r/state this))]
          (.destroy hammer)))})))
