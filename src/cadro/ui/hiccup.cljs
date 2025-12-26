(ns cadro.ui.hiccup
  (:require
   [reagent.core :as r]))

(defn wrap-content
  [props content]
  (if-let [[tag existing-props & body] content]
    (into [tag (r/merge-props existing-props props)]
          body)
    content))
