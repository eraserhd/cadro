(ns net.eraserhead.arbor.webui.events
  (:require
   [net.eraserhead.arbor :as arbor]
   [net.eraserhead.arbor.loci :as loci]
   [re-frame.core :as rf]))

(rf/reg-event-db
 ::initialize
 (constantly arbor/initial-state))

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
