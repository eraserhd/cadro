(ns cadro.ui.edit-panel
  (:require
   [cadro.bluetooth :as bt]
   [cadro.model :as model]
   [cadro.transforms :as tr]
   [cadro.ui.input :as input]
   [cadro.ui.panel :as panel]
   [clara.rules :as clara]
   [reagent.core :as ra]
   [re-frame.core :as rf]))

(def ^:private thing-to-edit (ra/atom nil))

(rf/reg-sub
  ::scales
  :<- [:session]
  (fn [session _]
    (model/scales session)))

(rf/reg-sub
  ::fixture-scales
  :<- [:session]
  (fn [session [_ fixture-id]]
    (->> (clara/query session model/fixture-scales :?fixture-id fixture-id)
         (map :?scale-id)
         (into #{}))))

(rf/reg-event-fx
 ::connect-clicked
 (fn [_ [_ device-id]]
   {::bt/connect device-id}))

(rf/reg-event-fx
 ::edit-panel-mounted
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(rf/reg-event-fx
 ::scale-checkbox-changed
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ fixture-id scale-id checked?]]
   (if checked?
     {:session (clara/insert session (model/asserted fixture-id ::model/spans scale-id))}
     {:session (clara/retract session (model/asserted fixture-id ::model/spans scale-id))})))

(defn scale-controls
  [fixture-id
   {scale-id ::model/id
    :keys [::model/displays-as
           ::model/raw-count]}
   associated-scales]
  [:li.scale
   [:input {:id        (str scale-id)
            :type      "checkbox"
            :checked   (contains? associated-scales scale-id)
            :on-change (fn [e]
                         (rf/dispatch
                          [::scale-checkbox-changed
                           fixture-id
                           scale-id
                           (.. e -target -checked)]))}]
   [:label {:for (str scale-id)}
    [:span.name displays-as]
    [:span.value raw-count]]
   [input/input {:id fixture-id
                 :attr ::model/transform
                 :lens (fn
                         ([t] (get-in t [::tr/scale displays-as] 1.0))
                         ([t v] (assoc-in t [::tr/scale displays-as] (js/parseFloat v))))
                 :label "Scaling Factor"}]])

(defn edit-panel []
  (rf/dispatch [::edit-panel-mounted])
  (fn []
    (when-let [fixture-id @thing-to-edit]
      (let [scales            @(rf/subscribe [::scales])
            associated-scales @(rf/subscribe [::fixture-scales fixture-id])]
        ^{:key (str fixture-id)}
        [panel/panel {:title "Edit Fixture"
                      :class "edit-panel"
                      :on-close #(reset! thing-to-edit nil)}
         [input/input {:id fixture-id
                       :attr ::model/displays-as
                       :label "Display Name"}]
         [:h2 "Scales"]
         (into [:ul.scale-controllers]
               (map (fn [{controller-id ::model/id
                          :keys [::model/displays-as
                                 ::model/hardware-address
                                 ::model/connection-status
                                 ::model/_controller]}]
                      ^{:key (str controller-id)}
                      [:li.scale-controller
                       [:span.name displays-as] " " [:span.hardware-address "(" hardware-address ")"]
                       [:div.scales
                        (case connection-status
                          (:connected)
                          (into [:ul.scales]
                                (map (fn [scale]
                                       [scale-controls fixture-id scale associated-scales]))
                                _controller)

                          (:disconnected)
                          [:button.btn
                           {:type     "button"
                            :on-click #(rf/dispatch [::connect-clicked controller-id])}
                           "Connect"]

                          (:connecting)
                          [:p "Connecting..."]

                          [:p "Unknown status??"])]]))
               scales)]))))

(rf/reg-event-fx
 ::edit-panel-opened
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(rf/reg-fx
 ::edit
 (fn [id]
   (reset! thing-to-edit id)
   (rf/dispatch [::edit-panel-opened])))
