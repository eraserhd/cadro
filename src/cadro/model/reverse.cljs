(ns cadro.model.reverse
  (:require-macros
   [clara.rules :as clara])
  (:require
   [clara-eav.eav :as eav]))

(defn reversed-attribute? [kw]
  (and (keyword? kw) (= \_ (first (name kw)))))

(defn reverse-attribute [kw]
  (keyword (namespace kw)
           (if (= \_ (first (name kw)))
             (subs (name kw) 1)
             (str \_ (name kw)))))

(clara/defrule reverse-attributes
  "Make derived reverse-attribute facts"
  [eav/EAV (= e ?e) (= a ?a) (= v ?v) (uuid? ?e) (uuid? ?v) (not (reversed-attribute? ?a))]
  =>
  (clara/insert! (eav/->EAV ?v (reverse-attribute ?a) ?e)))
