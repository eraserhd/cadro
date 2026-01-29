(ns cadro.ui.legend
  (:require
   [cadro.model :as model]
   [cadro.ui.gestures :as gestures]
   [cadro.ui.edit-panel :as edit-panel]
   [clara.rules :as clara]
   [clojure.set :as set]
   [clojure.string :as str]
   [re-frame.core :as rf]
   ["@fortawesome/react-fontawesome" :as fa]
   ["@fortawesome/free-solid-svg-icons" :as faSolid]))

(rf/reg-sub
 ::fixtures-and-points-trees
 :<- [:session]
 (fn [session]
   (model/fixtures-and-points-trees session)))

(rf/reg-event-fx
 ::new-machine-tapped
 [(rf/inject-cofx :session)]
 (fn [{:keys [session], :as all} _]
   (let [{:keys [id session]} (model/new-machine-tx session)]
     {:session session
      ::edit-panel/edit id})))

(rf/reg-event-fx
 ::point-tapped
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ id]]
   {:session (model/set-reference session id)}))

(rf/reg-event-fx
 ::legend-key-longpressed
 (fn [_ [_ id]]
   {::edit-panel/edit id}))

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
                                  :on-press #(rf/dispatch [::legend-key-longpressed id])}
                   [:button.point {:class [(if reference? "reference" "non-reference")]}
                    [:> fa/FontAwesomeIcon {:icon (if reference?
                                                    faSolid/faLocationCrosshairs
                                                    nil)
                                            :fixedWidth true}]
                    [:div.name-and-distance
                     [:span.displays-as displays-as]
                     (position-hiccup distance spans)]]]
                  [gestures/wrap {:on-press #(rf/dispatch [::legend-key-longpressed id])}
                   [:button.fixture {}
                    displays-as]])
                (when-not (empty? transforms)
                  [legend-keys transforms])]))
        transforms))

(defn legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   [legend-keys (-> @(rf/subscribe [::fixtures-and-points-trees])
                    model/propagate-spans
                    model/add-distances)]
   [:div.controls
    [new-machine-button]]])
