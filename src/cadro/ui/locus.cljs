(ns cadro.ui.locus
  (:require
   [cadro.model.object :as object]
   [cadro.ui.input :as input]
   [cadro.ui.panel :as panel]
   [reagent.core :as ra]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(def ^:private locus-to-edit (ra/atom nil))

(defn edit-panel []
  (fn []
    (when-let [eid @locus-to-edit]
      ^{:key (str eid)}
      [panel/panel {:title "Edit Locus"
                    :class "locus-edit-panel"
                    :on-close #(reset! locus-to-edit nil)}
       [input/input {:eid  eid
                     :attr ::object/display-name
                     :label "Display Name"}]])))

(rf/reg-fx
 ::edit
 (fn [eid]
   (reset! locus-to-edit eid)))
