(ns cadro.main
  (:require
   [cadro.db]
   [cadro.model.object]
   [cadro.model.locus]
   [cadro.ui.legend]
   [cadro.ui.locus]
   [clojure.spec.alpha :as s]
   [reagent.dom.client :as rdc]))

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn cadro []
  [:<>
   [cadro.ui.legend/legend]
   [cadro.ui.locus/edit-panel]])

(defn ^:dev/after-load start []
  (when ^boolean goog.DEBUG
    (s/check-asserts true))
  (cadro.db/connect!)
  (rdc/render root [cadro]))
