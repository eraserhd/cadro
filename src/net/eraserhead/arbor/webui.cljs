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
 ::machines
 (fn [_ _]
   [(rf/subscribe [::loci/db])])
 (fn [loci-db _]
   (loci/top-level loci-db)))

(defn- legend []
  [:div.floating-card.legend
   [:h1 "Legend"]
   (into [:ul]
         (map (fn [{:keys [::loci/name]}]
                [:li name]))
         @(rf/subscribe [::machines]))])

(defn- add-datum-command []
  [:button.icon [:i.fa-solid.fa-plus]])

(defn- machine-card [{:keys [::loci/id, ::loci/name]}]
  (let [name-value (r/atom name)]
    (fn machine-card* []
      [:div.machine
         [:input {:value @name-value
                  :on-change #(reset! name-value (.. % -target -value))
                  :on-blur #(rf/dispatch [::events/update-machine id ::loci/name @name-value])}]])))

(defn- settings-command []
  (let [dialog (r/atom nil)]
    (fn settings-command* []
      [:<>
       [:button.icon {:on-click #(.showModal @dialog)}
        [:i.fa-solid.fa-gear]]
       [:dialog.settings {:ref #(reset! dialog %),
                          :closedby "any"}
        [:h1 [:i.fa-solid.fa-gear] " Settings"]
        [:h2 "Machines"
          [:button.icon.new-machine {:on-click #(rf/dispatch [::events/new-machine])} [:i.fa-solid.fa-plus]]]
        (into [:<>]
              (map (fn [machine]
                     [machine-card machine]))
              @(rf/subscribe [::machines]))]])))

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
