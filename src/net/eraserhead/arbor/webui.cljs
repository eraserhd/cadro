(ns net.eraserhead.arbor.webui
  (:require
   [net.eraserhead.arbor.webui.bluetooth :as bt]
   [reagent.dom.client :as rdc]
   [re-frame.core :as rf]))

(defn- arbor []
  [:div {:style {:width "25%", :height "100%"}}
   "hello"])

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (rdc/render root [arbor]))
