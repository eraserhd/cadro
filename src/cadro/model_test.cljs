(ns cadro.model-test
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.test :refer [deftest testing is]]
   [datascript.core :as d]))

(deftest t-set-reference?-tx
  (let [conn     (d/create-conn (db/schema))
        refs     (fn [db]
                   (into #{} (d/q '[:find [?id ...]
                                    :where
                                    [?p ::model/reference? true]
                                    [?p ::model/id ?id]]
                                   db)))
        machine1 (db/squuid)
        point1   (db/squuid)
        point2   (db/squuid)
        point3   (db/squuid)
        _        (d/transact! conn [{::model/id         point1
                                     ::model/position   {"X" 42}}
                                    {::model/id         point2
                                     ::model/position   {"X" 107}
                                     ::model/reference? true}
                                    {::model/id         point3
                                     ::model/position   {"X" 99}}])
        tx       (model/set-reference?-tx @conn [::model/id point1])
        _        (d/transact! conn tx)
        db1      @conn
        tx       (model/set-reference?-tx @conn [::model/id point1])
        _        (d/transact! conn tx)
        db2      @conn]
    (is (= #{point1} (refs db1))
        "It sets an existing non-reference point to be reference.")
    (is (= #{point1} (refs db2))
        "An existing reference point is still reference.")))
