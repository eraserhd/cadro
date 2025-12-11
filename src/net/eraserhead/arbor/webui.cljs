(ns net.eraserhead.arbor.webui
  (:require
   [net.eraserhead.arbor.webui.bluetooth :as bt]
   [reagent.dom.client :as rdc]
   [re-frame.core :as rf]))

(defn- legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   [:ul
    [:li "one"]
    [:li "two"]
    [:li "three"]]
   [:div.controls
    [:button.icon "+"]]])

(defn- arbor []
  [:<>
   [legend]])

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (rdc/render root [arbor]))
