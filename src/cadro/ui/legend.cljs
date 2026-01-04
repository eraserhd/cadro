(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.locus :as locusui]
   [clojure.set :as set]
   [clojure.string :as str]
   [datascript.core :as d]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(re-posh/reg-sub
 ::loci-eids
 (fn [_ _]
   {:type  :query
    :query model/toplevel-loci-eids-q}))

(re-posh/reg-sub
 ::loci
 :<- [::loci-eids]
 (fn [eids]
   {:type    :pull-many
    :pattern model/toplevel-loci-pull
    :ids     eids}))

(re-posh/reg-event-fx
 ::new-machine-tapped
 [(re-posh/inject-cofx :ds)]
 (fn [{:keys [ds], :as all} _]
   (let [{:keys [id tx]} (model/new-machine-tx ds)]
     {:transact tx
      ::locusui/edit id})))

(re-posh/reg-event-ds
 ::locus-tapped
 (fn [ds [_ eid]]
   (when (::model/position (d/entity ds eid))
     (model/set-reference?-tx ds eid))))

(re-posh/reg-event-fx
 ::locus-longpressed
 (fn [_ [_ eid]]
   {::locusui/edit eid}))

(def new-machine-icon [:> fa/FontAwesomeIcon {:icon faSolid/faPlugCirclePlus}])
(defn new-machine-button []
  [:button.icon.new-machine
   {:on-click #(rf/dispatch [::new-machine-tapped])}
   new-machine-icon])

(defn- position-hiccup
  [position spans]
  (let [axes-names       (into {}
                               (map (juxt ::model/id ::model/display-name))
                               spans)
        axis-name->value (set/rename-keys position axes-names)]
    (into [:span.distance]
          (->> (sort (keys axis-name->value))
               (map (fn [axis-name]
                      [:span.component
                       [:span.axis-name axis-name]
                       [:span.axis-value (get axis-name->value axis-name)]]))))))

(defn- legend-keys
  [transforms]
  (into [:ul]
        (map (fn [{:keys [::model/id
                          ::model/display-name
                          ::model/reference?
                          ::model/position
                          ::model/spans
                          ::model/distance
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
                  (if distance
                    [:div.name-and-distance
                     [:span.display-name display-name]
                     (position-hiccup distance spans)]
                    display-name)]]
                (when-not (empty? transforms)
                  [legend-keys transforms])]))
        transforms))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   [legend-keys (-> @(re-posh/subscribe [::loci])
                    model/propagate-spans
                    model/add-distances)]
   [:div.controls
    [new-machine-button]]])
