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

(def loci-eids-q
  '[:find [?eid ...]
    :where
    [?eid ::model/transforms]
    (not [_ ::model/transforms ?eid])])

(re-posh/reg-sub
 ::loci-eids
 (fn [_ _]
   {:type  :query
    :query loci-eids-q}))

(def loci-pull
  '[::model/id
    ::model/display-name
    ::model/reference?
    ::model/position
    {::model/transforms ...}])

(re-posh/reg-sub
 ::loci
 :<- [::loci-eids]
 (fn [eids]
   {:type    :pull-many
    :pattern loci-pull
    :ids     eids}))

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

(defn- legend-keys
  [transforms]
  (into [:ul]
        (map (fn [{:keys [::model/id
                          ::model/display-name
                          ::model/reference?
                          ::model/position
                          ::model/transforms]}]
               ^{:key (str id)}
               [:li
                [gestures/wrap {:on-tap   #(rf/dispatch [::locus-tapped [::model/id id]])
                                :on-press #(rf/dispatch [::locus-longpressed [::model/id id]])}
                 [:button.locus {:class [(if reference?
                                           "reference"
                                           "non-reference")
                                         (when position
                                           "point")]}
                  [:> fa/FontAwesomeIcon {:icon (if reference?
                                                  faSolid/faLocationCrosshairs
                                                  nil)
                                          :fixedWidth true}]
                  display-name]]
                (when-not (empty? transforms)
                  [legend-keys transforms])]))
        transforms))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   [legend-keys @(re-posh/subscribe [::loci])]
   [:div.controls
    [new-machine-button]]])
