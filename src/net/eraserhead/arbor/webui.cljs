(ns net.eraserhead.arbor.webui
  (:require
   [net.eraserhead.arbor.loci :as loci]
   [net.eraserhead.arbor.webui.bluetooth :as bt]
   [net.eraserhead.arbor.webui.events :as events]
   [reagent.core :as r]
   [reagent.dom.client :as rdc]
   [re-frame.core :as rf]
   ["@fortawesome/fontawesome-free/js/all.js"]))

(rf/reg-sub
 ::loci/db
 (fn [app-db _]
   (::loci/db app-db)))

(rf/reg-sub
 ::loci
 (fn [_ _]
   [(rf/subscribe [::loci/db])])
 (fn [loci-db _]
   (loci/tree loci-db)))

(rf/reg-sub
 ::machines
 (fn [_ _]
   [(rf/subscribe [::loci/db])])
 (fn [loci-db _]
   (loci/top-level loci-db)))

(defn- legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [{:keys [::loci/id ::loci/name ::loci/origin?]}]
                ^{:key (str id)}
                [:li {:class [(when origin?
                                "origin")]}
                 name]))
         @(rf/subscribe [::loci]))])

(defn- add-datum-command []
  [:button.icon [:i.fa-solid.fa-plus]])

(defn- machine-card [{:keys [::loci/id, ::loci/name ::loci/device]}]
  (let [name-value (r/atom name)]
    (fn machine-card* []
      [:div.machine
       [:form
        [:label {:for "name"} "Machine Name"]
        [:input {:id (str "machine-" id "-name")
                 :name "name"
                 :value @name-value
                 :on-change #(reset! name-value (.. % -target -value))
                 :on-blur #(rf/dispatch [::events/update-machine id ::loci/name @name-value])}]
        [:label {:for "device"} "Device"]
        (let [current-device-value (or device "none")
              option               (fn [id text]
                                     ^{:key id}
                                     [:option (cond-> {:value id}
                                                (= id current-device-value) (assoc :selected true))
                                      text])]
          (into [:select {:name "device"
                          :on-change #(rf/dispatch [::events/update-machine id ::loci/device (.. % -target -value)])}
                 (option "none" "--None--")]
                (map (fn [[id {:keys [name]}]]
                       (option id name)))
                @(rf/subscribe [::bt/devices])))]])))

(defn- settings-command []
  (let [dialog (r/atom nil)]
    (fn settings-command* []
      [:<>
       [:button.icon {:on-click #(do
                                   (rf/dispatch [::bt/fetch-device-list])
                                   (.showModal @dialog))}
        [:i.fa-solid.fa-gear]]
       [:dialog.settings {:ref #(reset! dialog %),
                          :closedby "any"}
        [:h1 [:i.fa-solid.fa-gear] " Settings"]
        [:h2 "Machines"]
        (into [:<>]
              (map (fn [{:keys [::loci/id], :as machine}]
                     ^{:key (str id)}
                     [machine-card machine]))
              @(rf/subscribe [::machines]))
        [:div.machine-commands
         [:button.icon.new-machine {:on-click #(rf/dispatch [::events/new-machine])}
          [:i.fa-solid.fa-plus]]]]])))

(defn- command-bar []
  [:div.floating-card.command-bar
   [add-datum-command]
   [settings-command]])

(defn- arbor []
  [:<>
   [legend]
   [command-bar]])

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (rf/dispatch-sync [::events/initialize])
  (rdc/render root [arbor]))
