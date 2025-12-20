(ns net.eraserhead.arbor.webui.loci
  (:require
   [net.eraserhead.arbor.loci :as loci]
   [net.eraserhead.arbor.loci.scale :as scale]
   [re-frame.core :as rf]))

(rf/reg-sub
 ::loci/db
 (fn [app-db _]
   (::loci/db app-db)))

(rf/reg-sub
 ::scale/devices
 (fn [_ _]
   [(rf/subscribe [::loci/db])])
 (fn [{:keys [::scale/devices]} _]
   devices))
