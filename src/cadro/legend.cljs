(ns cadro.legend
  (:require
   [cadro.db :as db]
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [posh.reagent :as p]))

(defn legend-key [id]
  ;(let [{:keys [::object/id ::object/display-name]} @(re-posh/subscribe [::locus id])]
    ^{:key id}
    [:li id])

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [dbid]
                [legend-key dbid]))
         @(p/q '[:find [?id ...]
                 :in $
                 :where [?id ::locus/offset]]
               db/conn))])
