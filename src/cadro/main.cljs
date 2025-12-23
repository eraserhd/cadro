(ns cadro.main
  (:require
   [cadro.db]
   [cadro.legend]
   [clojure.spec.alpha :as s]
   [reagent.dom.client :as rdc]))

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (when ^boolean goog.DEBUG
    (s/check-asserts true))
  (rdc/render root
              [cadro.legend/legend]))
