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
 ::top-level-fixture-ids
 (fn [_ _]
   {:type  :query
    :query model/top-level-fixture-eids-q}))

(re-posh/reg-sub
 ::fixtures-and-points-trees
 :<- [::top-level-fixture-ids]
 (fn [eids]
   {:type    :pull-many
    :pattern model/fixtures-and-points-trees-pull
    :ids     eids}))

(rf/reg-event-fx
 ::new-machine-tapped
 [(re-posh/inject-cofx :ds)
  (rf/inject-cofx :session)]
 (fn [{:keys [ds session], :as all} _]
   (let [{:keys [id tx session]} (model/new-machine-tx ds session)]
     {:transact tx
      :session session
      ::edit-panel/edit id})))

(rf/reg-event-fx
 ::point-tapped
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ id]]
   {:session (model/set-reference session id)}))

(rf/reg-event-fx
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
                               (map (juxt ::model/id ::model/displays-as))
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
                          ::model/displays-as
                          ::model/reference?
                          ::model/coordinates
                          ::model/spans
                          ::model/distance
                          ::model/transforms]}]
               ^{:key (str id)}
               [:li
                (if coordinates
                  [gestures/wrap {:on-tap   #(rf/dispatch [::point-tapped id])
                                  :on-press #(rf/dispatch [::legend-key-longpressed [::model/id id]])}
                   [:button.point {:class [(if reference? "reference" "non-reference")]}
                    [:> fa/FontAwesomeIcon {:icon (if reference?
                                                    faSolid/faLocationCrosshairs
                                                    nil)
                                            :fixedWidth true}]
                    [:div.name-and-distance
                     [:span.displays-as displays-as]
                     (position-hiccup distance spans)]]]
                  [gestures/wrap {:on-press #(rf/dispatch [::legend-key-longpressed [::model/id id]])}
                   [:button.fixture {}
                    displays-as]])
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
