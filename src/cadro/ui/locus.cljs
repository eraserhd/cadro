(ns cadro.ui.locus
  (:require
   [reagent.core :as ra]
   [re-frame.core :as rf]))

(def ^:private locus-to-edit (ra/atom nil))

(defn edit-panel []
  (when-let [eid @locus-to-edit]
    [:div.floating-card.locus-edit-panel
     [:h1 "Edit Locus"]
     "hello"]))

(rf/reg-fx
 ::edit
 (fn [eid]
   (reset! locus-to-edit eid)))
