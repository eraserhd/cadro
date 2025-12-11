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
    [:li "three"]]])

(defn- command-bar []
  [:div.floating-card.command-bar
   [:button.icon "+"]
   [:button.icon "s"]])

(defn- arbor []
  [:<>
   [legend]
   [command-bar]])

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (rdc/render root [arbor]))
