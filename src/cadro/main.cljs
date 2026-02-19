(ns cadro.main
  (:require
   [cadro.bluetooth :as bt]
   [cadro.model]
   [cadro.session :as session]
   [cadro.ui.axes]
   [cadro.ui.legend]
   [cadro.ui.edit-panel]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha]
   [reagent.dom.client :as rdc]
   [re-frame.core :as rf]
   ["@capacitor-community/keep-awake" :refer [KeepAwake]]))

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn cadro []
  [:<>
   [cadro.ui.axes/axes-panel]
   [cadro.ui.legend/legend]
   [cadro.ui.edit-panel/edit-panel]])

(rf/reg-event-fx
 ::initialize
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(defn ^:dev/after-load start []
  (when ^boolean goog.DEBUG
    (clojure.spec.test.alpha/instrument)
    (s/check-asserts true))
  (-> (.keepAwake KeepAwake)
      (.then #(js/console.log "Screen will stay awake"))
      (.catch #(js/console.error "Failed to keep screen awake:" %)))
  (session/init-from-storage!)
  (rf/dispatch-sync [::initialize])
  (rdc/render root [cadro]))
