(ns cadro.ui.edit-panel
  (:require
   [cadro.bluetooth :as bt]
   [cadro.model :as model]
   [cadro.ui.input :as input]
   [cadro.ui.panel :as panel]
   [reagent.core :as ra]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(def ^:private thing-to-edit (ra/atom nil))

(re-posh/reg-sub
 ::scale-eids
 (fn []
   {:type :query
    :query '[:find [?id ...]
             :where [?id ::model/hardware-address]]}))

(def scale-pull
  '[::model/id
    ::model/display-name
    ::model/hardware-address
    ::model/connection-status
    {::model/_controller [::model/id
                          ::model/display-name
                          ::model/raw-count]}])

(re-posh/reg-sub
 ::scales
 :<- [::scale-eids]
 (fn [eids _]
   {:type    :pull-many
    :pattern scale-pull
    :ids      eids}))

(def fixture-pull
  '[{::model/spans
     [::model/id]}])

(re-posh/reg-sub
 ::locus
 (fn [_ [_ locus-id]]
   {:type    :pull
    :pattern fixture-pull
    :id      locus-id}))

(rf/reg-event-fx
 ::connect-clicked
 (fn [_ [_ device-id]]
   {::bt/connect device-id}))

(rf/reg-event-fx
 ::edit-panel-mounted
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(re-posh/reg-event-ds
 ::scale-checkbox-changed
 (fn [ds [_ fixture-id scale-id checked?]]
   (if checked?
     (model/associate-scale-tx ds fixture-id scale-id)
     (model/dissociate-scale-tx ds fixture-id scale-id))))

(defn scale-controls
  [fixture-id
   {scale-id ::model/id
    :keys [::model/display-name
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
                           [::model/id scale-id]
                           (.. e -target -checked)]))}]
   [:label {:for (str scale-id)}
    [:span.name display-name]
    [:span.value raw-count]]])

(defn edit-panel []
  (rf/dispatch [::edit-panel-mounted])
  (fn []
    (when-let [fixture-id @thing-to-edit]
      (let [scales            @(re-posh/subscribe [::scales])
            locus             @(re-posh/subscribe [::locus fixture-id])
            associated-scales (->> (::model/spans locus)
                                   (map ::model/id)
                                   (into #{}))]
        ^{:key (str fixture-id)}
        [panel/panel {:title "Edit Fixture"
                      :class "edit-panel"
                      :on-close #(reset! thing-to-edit nil)}
         [input/input {:eid  fixture-id
                       :attr ::model/display-name
                       :label "Display Name"}]
         [:h2 "Scales"]
         (into [:ul.scale-controllers]
               (map (fn [{controller-id ::model/id
                          :keys [::model/display-name
                                 ::model/hardware-address
                                 ::model/connection-status
                                 ::model/_controller]}]
                      ^{:key (str controller-id)}
                      [:li.scale-controller
                       [:span.name display-name] " " [:span.hardware-address "(" hardware-address ")"]
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
                            :on-click #(rf/dispatch [::connect-clicked [::model/id controller-id]])}
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
 (fn [eid]
   (reset! thing-to-edit eid)
   (rf/dispatch [::edit-panel-opened])))
