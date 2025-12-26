(ns net.eraserhead.arbor.webui.events
  (:require
   [net.eraserhead.arbor :as arbor]
   [net.eraserhead.arbor.loci :as loci]
   [re-frame.core :as rf]))

(rf/reg-fx
 ::focus-control
 (fn [id]
   (js/setTimeout
    (fn []
      (when-let [ctl (.getElementById js/document id)]
        (.focus ctl)))
    0)))

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   nil))

(rf/reg-event-fx
 ::new-machine
 (fn [{:keys [db]} _]
   (let [id          (random-uuid)
         new-machine {::loci/id     id
                      ::loci/name   "New Machine"
                      ::loci/parent nil}]
     {:db (update db ::loci/db loci/conj new-machine)
      ::focus-control (str "machine-" id "-name")})))

(rf/reg-event-db
 ::update-machine
 (fn [app-db [_ id k v]]
   (update app-db ::loci/db loci/update id #(assoc % k v))))
