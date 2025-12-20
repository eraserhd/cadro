(ns net.eraserhead.arbor.webui.loci
  (:require
   [net.eraserhead.arbor.loci :as loci]
   [re-frame.core :as rf]))

(rf/reg-sub
 ::loci/db
 (fn [app-db _]
   (::loci/db app-db)))
