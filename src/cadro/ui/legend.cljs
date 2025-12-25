(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.ui.locus :as locusui]
   [cadro.ui.tappable :as tappable]
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
   (let [id (random-uuid)]
     {:transact [{::object/id           id
                  ::object/display-name "New Machine"
                  ::locus/offset        {"x" 42}}]
      ::locusui/edit [::object/id id]})))

(def new-machine-icon [:i.fa-solid.fa-plug-circle-plus])
(defn new-machine-button []
  [:button.icon.new-machine
   {:on-click #(rf/dispatch [::new-machine])}
   new-machine-icon])

(defn legend-key [eid]
  (let [{:keys [::object/id ::object/display-name]} @(re-posh/subscribe [::locus eid])]
    [:li [tappable/tappable {:on-tap #(println "tapped")
                             :on-press #(println "pressed")}
          display-name]]))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [dbid]
                ^{:key (str dbid)}
                [legend-key dbid]))
         @(re-posh/subscribe [::loci-tree]))
   [new-machine-button]])
