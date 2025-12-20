(ns net.eraserhead.arbor.webui
  (:require
   [clojure.spec.alpha :as s]
   [net.eraserhead.arbor.loci :as loci]
   [net.eraserhead.arbor.loci.scale :as scale]
   [net.eraserhead.arbor.webui.bluetooth :as btui]
   [net.eraserhead.arbor.webui.events :as events]
   [net.eraserhead.arbor.webui.loci :as lociui]
   [reagent.core :as r]
   [reagent.dom.client :as rdc]
   [re-frame.core :as rf]
   ["@fortawesome/fontawesome-free/js/all.js"]))

(rf/reg-sub
 ::origin-stack
 (fn [_ _]
   [(rf/subscribe [::loci/db])])
 (fn [loci-db _]
   (loci/origin-stack loci-db)))

(defn- axes-card []
  (fn []
    (let [[{:keys [::loci/device]}] @(rf/subscribe [::origin-stack])
          devices                   @(rf/subscribe [::scale/devices])
          axes                      (get-in devices [device ::scale/axes])]
      (into [:div.floating-card.axes
             [:h1 "Axes"]]
            (map (fn [[axis-name axis-value]]
                   [:div.axis
                    [:div.name axis-name]
                    [:div.value axis-value]
                    [:button.icon [:i.fa-solid.fa-location-dot]]]))
            axes))))

(defn- add-datum-command []
  [:button.icon [:i.fa-solid.fa-plus]])

(defn- device-option [id text]
  ^{:key id}
  [:option {:value id}
   text])

(defn- machine-card [{:keys [::loci/id, ::loci/name ::loci/device]}]
  (let [name-value (r/atom name)]
    (fn machine-card* []
      [:div.machine
       [:form
        [:label {:for "name"} "Machine Name"]
        [:input {:id        (str "machine-" id "-name")
                 :name      "name"
                 :value     @name-value
                 :on-change #(reset! name-value (.. % -target -value))
                 :on-blur   #(rf/dispatch [::events/update-machine id ::loci/name @name-value])}]
        [:label {:for "device"} "Device"]
        (into [:select {:name          "device"
                        :default-value (or device "none")
                        :on-change     (fn [e]
                                         (let [value (.. e -target -value)]
                                           (rf/dispatch [::events/update-machine id ::loci/device value])
                                           (rf/dispatch [::btui/connect value])))}
               (device-option "none" "--None--")]
              (map (fn [[id {:keys [::scale/name]}]]
                     (device-option id name)))
              @(rf/subscribe [::scale/devices]))]])))

(defn- settings-command []
  (let [dialog (r/atom nil)]
    (fn settings-command* []
      [:<>
       [:button.icon {:on-click #(do
                                   (rf/dispatch [::btui/fetch-device-list])
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
              @(rf/subscribe [::loci/top-level]))
        [:div.machine-commands
         [:button.icon.new-machine {:on-click #(rf/dispatch [::events/new-machine])}
          [:i.fa-solid.fa-plus]]]]])))

(defn- command-bar []
  (let [log-visible? (r/atom false)]
    (fn []
      [:<>
       [axes-card]
       [:div.floating-card.command-bar
        [add-datum-command]
        [:button.icon {:on-click (fn [_]
                                   (swap! log-visible? #(not %)))}
         lociui/device-log-icon]
        [settings-command]]
       (when @log-visible?
         [lociui/device-log])])))

(defn- arbor []
  [:<>
   [lociui/legend]
   [command-bar]])

(defonce root (rdc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load start []
  (when ^boolean goog.DEBUG
    (s/check-asserts true))
  (rf/dispatch-sync [::events/initialize])
  (rdc/render root [arbor]))
