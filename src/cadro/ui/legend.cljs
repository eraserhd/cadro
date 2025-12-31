(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.model.locus :as locus]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.locus :as locusui]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))


(def loci-tree-q
  '[:find [?eid ...]
    :where
    [?eid ::model/transforms]
    #_ ;; FIXME: I think posh can't analyze this part?
    (not [_ ::model/transforms ?eid])])

(re-posh/reg-sub
 ::loci-tree
 (fn [_ _]
   {:type  :query
    :query loci-tree-q}))

(re-posh/reg-sub
 ::locus
 (fn [_ [_ eid]]
   {:type    :pull
    :pattern '[::model/id
               ::model/display-name
               ::model/reference?]
    :id      eid}))

(re-posh/reg-event-fx
 ::new-machine-tapped
 [(re-posh/inject-cofx :ds)]
 (fn [{:keys [ds], :as all} _]
   (let [{:keys [id tx]} (locus/new-machine-tx ds)]
     {:transact tx
      ::locusui/edit id})))

(re-posh/reg-event-ds
 ::locus-tapped
 (fn [ds [_ eid]]
   (locus/set-reference-tx ds eid)))

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
  (let [{:keys [::model/id
                ::model/display-name
                ::model/reference?]}
        @(re-posh/subscribe [::locus eid])]
     [gestures/wrap {:on-tap   #(rf/dispatch [::locus-tapped [::model/id id]])
                     :on-press #(rf/dispatch [::locus-longpressed [::model/id id]])}
      [:button.locus {:class [(if reference?
                                "reference"
                                "non-reference")]}
       [:> fa/FontAwesomeIcon {:icon (if reference?
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
