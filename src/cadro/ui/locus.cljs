(ns cadro.ui.locus
  (:require
   [cadro.bluetooth :as bt]
   [cadro.model.locus :as locus]
   [cadro.model.object :as object]
   [cadro.model.scale :as scale]
   [cadro.model.scale-controller :as scale-controller]
   [cadro.ui.input :as input]
   [cadro.ui.panel :as panel]
   [reagent.core :as ra]
   [re-frame.core :as rf]
   [re-posh.core :as re-posh]))

(def ^:private locus-to-edit (ra/atom nil))

(re-posh/reg-sub
 ::scale-eids
 (fn []
   {:type :query
    :query '[:find [?id ...]
             :where [?id ::scale-controller/address]]}))

(def scale-pull
  '[::object/id
    ::object/display-name
    ::scale-controller/address
    ::scale-controller/status
    {::scale/_controller [::object/id
                          ::object/display-name
                          ::scale/raw-value]}])

(re-posh/reg-sub
 ::scales
 :<- [::scale-eids]
 (fn [eids _]
   {:type    :pull-many
    :pattern scale-pull
    :ids      eids}))

(def locus-pull
  '[{::locus/locus-scale
     [{::scale/scale
       [::object/id]}]}])

(re-posh/reg-sub
 ::locus
 (fn [_ [_ locus-id]]
   {:type    :pull
    :pattern locus-pull
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
 (fn [ds [_ locus-id scale-id checked?]]
   (if checked?
     (locus/associate-scale-tx ds locus-id scale-id)
     (locus/dissociate-scale-tx ds locus-id scale-id))))

(defn scale-controls
  [locus-id
   {scale-id ::object/id
    :keys [::object/display-name
           ::scale/raw-value]}
   associated-scales]
  [:li.scale
   [:input {:id        (str scale-id)
            :type      "checkbox"
            :checked   (contains? associated-scales scale-id)
            :on-change (fn [e]
                         (rf/dispatch
                          [::scale-checkbox-changed
                           locus-id
                           [::object/id scale-id]
                           (.. e -target -checked)]))}]
   [:label {:for (str scale-id)}
    [:span.name display-name]
    [:span.value raw-value]]])

(defn edit-panel []
  (rf/dispatch [::edit-panel-mounted])
  (fn []
    (when-let [locus-id @locus-to-edit]
      (let [scales            @(re-posh/subscribe [::scales])
            locus             @(re-posh/subscribe [::locus locus-id])
            associated-scales (->> (::locus/locus-scale locus)
                                   (map ::scale/scale)
                                   (map ::object/id)
                                   (into #{}))]
        ^{:key (str locus-id)}
        [panel/panel {:title "Edit Locus"
                      :class "locus-edit-panel"
                      :on-close #(reset! locus-to-edit nil)}
         [input/input {:eid  locus-id
                       :attr ::object/display-name
                       :label "Display Name"}]
         [:h2 "Scales"]
         (into [:ul.scale-controllers]
               (map (fn [{controller-id ::object/id
                          :keys [::object/display-name
                                 ::scale-controller/address
                                 ::scale-controller/status
                                 ::scale/_controller]}]
                      ^{:key (str controller-id)}
                      [:li.scale-controller
                       [:span.name display-name] " " [:span.address "(" address ")"]
                       [:div.scales
                        (case status
                          (:connected)
                          (into [:ul.scales]
                                (map (fn [scale]
                                       [scale-controls locus-id scale associated-scales]))
                                _controller)

                          (:disconnected)
                          [:button.btn
                           {:type     "button"
                            :on-click #(rf/dispatch [::connect-clicked [::object/id controller-id]])}
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
   (reset! locus-to-edit eid)
   (rf/dispatch [::edit-panel-opened])))
