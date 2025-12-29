(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.locus :as locusui]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))


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
               ::object/display-name
               ::locus/_reference]
    :id      eid}))

(re-posh/reg-event-fx
 ::new-machine-tapped
 (fn [{:keys [ds]} _]
   (let [{:keys [id tx]} (locus/new-machine-tx)]
     {:transact tx
      ::locusui/edit id})))

(re-posh/reg-event-ds
 ::locus-tapped
 (fn [ds [_ eid]]
   (locus/set-reference-tx eid)))

(re-posh/reg-event-fx
 ::locus-longpressed
 (fn [_ [_ eid]]
   {::locusui/edit eid}))

(def new-machine-icon [:> fa/FontAwesomeIcon {:icon faSolid/faPlugCirclePlus}])
(defn new-machine-button []
  [:button.icon.new-machine
   {:on-click #(rf/dispatch [::new-machine-tapped])}
   new-machine-icon])

(defn legend-key [eid]
  (let [{:keys [::object/id
                ::object/display-name
                ::locus/_reference]}
        @(re-posh/subscribe [::locus eid])]
     [gestures/wrap {:on-tap   #(rf/dispatch [::locus-tapped [::object/id id]])
                     :on-press #(rf/dispatch [::locus-longpressed [::object/id id]])}
      [:button.locus {:class [(if _reference
                                "reference"
                                "non-reference")]}
       [:> fa/FontAwesomeIcon {:icon (if _reference
                                       faSolid/faLocationCrosshairs
                                       nil)
                               :fixedWidth true}]
       display-name]]))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [dbid]
                ^{:key (str dbid)}
                [:li [legend-key dbid]])
           @(re-posh/subscribe [::loci-tree])))
   [:div.controls
    [new-machine-button]]])
