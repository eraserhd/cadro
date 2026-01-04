(ns cadro.ui.legend
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.edit-panel :as edit-panel]
   [clojure.set :as set]
   [clojure.string :as str]
   [datascript.core :as d]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(re-posh/reg-sub
 ::top-level-fixture-eids
 (fn [_ _]
   {:type  :query
    :query model/top-level-fixture-eids-q}))

(re-posh/reg-sub
 ::fixtures-and-points-trees
 :<- [::top-level-fixture-eids]
 (fn [eids]
   {:type    :pull-many
    :pattern model/fixtures-and-points-trees-pull
    :ids     eids}))

(re-posh/reg-event-fx
 ::new-machine-tapped
 [(re-posh/inject-cofx :ds)]
 (fn [{:keys [ds], :as all} _]
   (let [{:keys [id tx]} (model/new-machine-tx ds)]
     {:transact tx
      ::edit-panel/edit id})))

(re-posh/reg-event-ds
 ::point-tapped
 (fn [ds [_ eid]]
   (when (::model/position (d/entity ds eid))
     (model/set-reference?-tx ds eid))))

(re-posh/reg-event-fx
 ::legend-key-longpressed
 (fn [_ [_ eid]]
   {::edit-panel/edit eid}))

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
                (if position
                  [gestures/wrap {:on-tap   #(rf/dispatch [::point-tapped [::model/id id]])
                                  :on-press #(rf/dispatch [::legend-key-longpressed [::model/id id]])}
                   [:button.point {:class [(if reference? "reference" "non-reference")]}
                    [:> fa/FontAwesomeIcon {:icon (if reference?
                                                    faSolid/faLocationCrosshairs
                                                    nil)
                                            :fixedWidth true}]
                    [:div.name-and-distance
                     [:span.display-name display-name]
                     (position-hiccup distance spans)]]]
                  [gestures/wrap {:on-press #(rf/dispatch [::legend-key-longpressed [::model/id id]])}
                   [:button.fixture {}
                    display-name]])
                (when-not (empty? transforms)
                  [legend-keys transforms])]))
        transforms))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   [legend-keys (-> @(re-posh/subscribe [::fixtures-and-points-trees])
                    model/propagate-spans
                    model/add-distances)]
   [:div.controls
    [new-machine-button]]])
