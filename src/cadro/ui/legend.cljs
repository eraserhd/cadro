(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.locus :as locusui]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/fontawesome-free/js/all.js"]))


(re-posh/reg-sub
 ::loci-tree
 (fn [_ _]
   {:type  :query
    :query '[:find [?id ...]
             :where [?id ::locus/offset]]}))

(re-posh/reg-sub
 ::locus
 (fn [_ [_ eid]]
   {:type    :pull
    :pattern '[::object/id
               ::object/display-name]
    :id      eid}))

(re-posh/reg-event-fx
 ::new-machine
 (fn [{:keys [ds]} _]
   (let [{:keys [id tx]} (locus/new-machine-tx)]
     {:transact tx
      ::locusui/edit id})))

(re-posh/reg-event-fx
 ::edit-locus
 (fn [_ [_ eid]]
   {::locusui/edit eid}))

(def new-machine-icon [:i.fa-solid.fa-plug-circle-plus])
(defn new-machine-button []
  [:button.icon.new-machine
   {:on-click #(rf/dispatch [::new-machine])}
   new-machine-icon])

(defn legend-key [eid]
  (let [{:keys [::object/id ::object/display-name]} @(re-posh/subscribe [::locus eid])]
    ^{:key (str id)}
    [:li [gestures/wrap {:on-tap #(println "tapped")
                         :on-press #(rf/dispatch [::edit-locus [::object/id id]])}
          [:button display-name]]]))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [dbid]
                ^{:key (str dbid)}
                [legend-key dbid]))
         @(re-posh/subscribe [::loci-tree]))
   [new-machine-button]])
