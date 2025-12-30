(ns cadro.ui.locus
  (:require
   [cadro.bluetooth :as bt]
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

(rf/reg-event-fx
 ::connect-clicked
 (fn [_ [_ device-id]]
   {::bt/connect device-id}))

(rf/reg-event-fx
 ::edit-panel-mounted
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(defn edit-panel []
  (rf/dispatch [::edit-panel-mounted])
  (fn []
    (when-let [eid @locus-to-edit]
      ^{:key (str eid)}
      [panel/panel {:title "Edit Locus"
                    :class "locus-edit-panel"
                    :on-close #(reset! locus-to-edit nil)}
       [input/input {:eid  eid
                     :attr ::object/display-name
                     :label "Display Name"}]
       [:h2 "Scales"]
       (into [:ul.scale-controllers]
             (map (fn [{:keys [::object/id
                               ::object/display-name
                               ::scale-controller/address
                               ::scale-controller/status
                               ::scale/_controller]}]
                    ^{:key (str id)}
                    [:li.scale-controller
                     [:span.name display-name] " " [:span.address "(" address ")"]
                     [:div.scales
                      (case status
                        (:connected)
                        (into [:ul.scales]
                              (map (fn [{:keys [::object/id
                                                ::object/display-name
                                                ::scale/raw-value]}]
                                     [:li.scale
                                      [:input {:id (str id)
                                               :type "checkbox"}]
                                      [:label {:for (str id)}
                                       [:span.name display-name]
                                       [:span.value raw-value]]]))
                              _controller)

                        (:disconnected)
                        [:button.btn
                         {:on-click #(rf/dispatch [::connect-clicked [::object/id id]])}
                         "Connect"]

                        (:connecting)
                        [:p "Connecting..."]

                        [:p "Unknown status??"])]]))
             @(re-posh/subscribe [::scales]))])))

(rf/reg-event-fx
 ::edit-panel-opened
 (fn [_ _]
   {::bt/fetch-device-list nil}))

(rf/reg-fx
 ::edit
 (fn [eid]
   (reset! locus-to-edit eid)
   (rf/dispatch [::edit-panel-opened])))
