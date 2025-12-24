(ns cadro.ui.locus
  (:require
   [cadro.model.object :as object]
   [cadro.ui.input :as input]
   [reagent.core :as ra]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(def ^:private locus-to-edit (ra/atom nil))

(defn edit-panel []
  (fn []
    (when-let [eid @locus-to-edit]
      [:div.floating-card.locus-edit-panel
       [:h1 "Edit Locus"]
       [:form
        [:label {:for "display_name"}
         "Display Name"]
        [input/input eid ::object/display-name]]])))

(rf/reg-fx
 ::edit
 (fn [eid]
   (reset! locus-to-edit eid)))
