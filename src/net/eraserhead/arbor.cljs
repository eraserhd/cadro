(ns net.eraserhead.arbor
  (:require
   [reagent.dom :as rd]
   [reagent.dom.client :as rdomc]))

(defonce root (rdomc/create-root (js/document.getElementById "app")))

(defn- arbor []
  [:ul
   [:li "hello"]])

(defn ^:dev/after-load start []
  (rdomc/render root [arbor]))
