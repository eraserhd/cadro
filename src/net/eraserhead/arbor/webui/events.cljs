(ns net.eraserhead.arbor.webui.events
  (:require
   [net.eraserhead.arbor :as arbor]
   [net.eraserhead.arbor.loci :as loci]
   [net.eraserhead.arbor.webui.storage :as storage]
   [re-frame.core :as rf]))

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   (or (storage/load-db)
       arbor/initial-state)))

(rf/reg-global-interceptor
  (rf/->interceptor
    :id :persist-db-to-localStorage
    :after (fn [context]
             (when-let [db (rf/get-effect context :db)]
               (storage/store-db db))
             context)))

(rf/reg-event-db
 ::new-machine
 (fn [app-db _]
   (let [new-machine {::loci/id (random-uuid)
                      ::loci/name "New Machine"
                      ::loci/parent nil}]
     (update app-db ::loci/db loci/conj new-machine))))

(rf/reg-event-db
 ::update-machine
 (fn [app-db [_ id k v]]
   (update app-db ::loci/db loci/update id #(assoc % k v))))
