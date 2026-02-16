(ns cadro.model.facts
  (:require-macros
   [clara.rules :as clara])
  (:require
   [clara.rules :as clara]
   [clara-eav.eav :as eav]))

(defn asserted [e a v]
  (assoc (eav/->EAV e a v) :persistent? true))

(defn derived [e a v]
  (eav/->EAV e a v))

(clara/defquery fact-values
  [?e ?a]
  [?fact <- eav/EAV (= ?e e) (= ?a a) (= ?v v)])

(defn upsert
  "Insert triple, retracting any pre-existing values for it.
   Note: Caller should fire-rules before first upsert and after all upserts."
  [session e a v]
  (let [existing-facts (map :?fact (clara/query session fact-values :?e e :?a a))]
    (if (and (= 1 (count existing-facts)) (= v (:?v (first existing-facts))))
      session
      (as-> session $
        (apply clara/retract $ existing-facts)
        (clara/insert $ (asserted e a v))))))

(clara/defquery persistent-facts []
  [?fact <- eav/EAV (= e ?e) (= a ?a) (= v ?v)]
  [:test (:persistent? ?fact)])
