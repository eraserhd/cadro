(ns cadro.model.scale-controller-test
  (:require
   [cadro.db :as db]
   [cadro.model.object :as object]
   [cadro.model.scale-controller :as scale-controller]
   [clojure.test :refer [deftest is]]
   [datascript.core :as d]))

(deftest t-add-controllers
  (let [conn      (d/create-conn (db/schema))
        result-tx (scale-controller/add-controllers-tx @conn [{::object/display-name      "Nexus 7"
                                                               ::scale-controller/address "00:00:01"}
                                                              {::object/display-name      "HC-06"
                                                               ::scale-controller/address "02:03:04"}])
        _         (d/transact! conn result-tx)
        pull      [::object/id
                   ::object/display-name
                   ::scale-controller/address
                   ::scale-controller/status]
        c1        (d/pull @conn pull [::scale-controller/address "00:00:01"])
        c2        (d/pull @conn pull [::scale-controller/address "02:03:04"])]
    (is (= {::object/display-name "Nexus 7"
            ::scale-controller/address "00:00:01"
            ::scale-controller/status :disconnected}
           (dissoc c1 ::object/id))
        "It stores the 'Nexus 7' controller, marking as disconnected.")
    (is (= {::object/display-name "HC-06"
            ::scale-controller/address "02:03:04"
            ::scale-controller/status :disconnected}
           (dissoc c2 ::object/id))
        "It stores the 'HC-06' controller, marking as disconnected.")
    (is (uuid? (::object/id c1))
        "It creates a UUID for 'Nexus 7'.")
    (is (uuid? (::object/id c2))
        "It creates a UUID for 'HC-06'.")))
