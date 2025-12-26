(ns cadro.ui.hiccup
  (:require
   [reagent.core :as r]))

(defn normalize
  [content]
  (let [[tag props & more] content]
    (cond
     (map? props)                   (into [tag props] more)
     (and (nil? props) (nil? more)) [tag {}]
     :else                          (into [tag {} props] more))))

(defn wrap-content
  [props content]
  (if-let [[tag existing-props & body] content]
    (into [tag (r/merge-props existing-props props)]
          body)
    content))
