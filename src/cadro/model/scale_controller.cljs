(ns cadro.model.scale-controller
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datascript.core :as d]))

(defn add-received-data-tx
  [ds controller-id data]
  {:pre [(d/db? ds)
         (string? data)]}
  (let [to-process                    (-> (str (::model/receive-buffer (d/entity ds controller-id))
                                               data)
                                          (str/replace #"[;\s]+" ";")
                                          (str/replace #"^;+" ""))
        [to-process new-scale-values] (loop [to-process       to-process
                                             new-scale-values {}]
                                        (if-let [[_ axis value-str left] (re-matches #"^([a-zA-Z])(-?\d+(?:\.\d*)?);(.*)" to-process)]
                                          (recur left (assoc new-scale-values axis (* value-str 1.0)))
                                          [to-process new-scale-values]))]
    (concat
      [[:db/add controller-id ::model/receive-buffer to-process]]
      (mapcat (fn [[scale-name value]]
                (model/upsert-raw-count-tx ds controller-id scale-name value))
              new-scale-values))))
