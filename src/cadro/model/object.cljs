(ns cadro.model.object
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::object (s/keys :req [::id] :opt [::display-name]))

(s/def ::id uuid?)
(s/def ::display-name string?)
