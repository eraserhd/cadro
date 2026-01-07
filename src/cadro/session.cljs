(ns cadro.session
  (:require-macros
   [clara.rules :as clara])
  (:require
   [clojure.spec.alpha :as s]
   [clara.rules :as clara]
   [re-frame.core :as r]))

(clara/defsession empty-session)

(defonce session (atom empty-session))

(s/def ::insert (s/coll-of any?))
(s/def ::retract (s/coll-of any?))
(s/def ::session-args (s/keys :req-un [::insert ::retract]))

(r/reg-fx
 :session
 (fn [{:keys [retract insert], :as session-args}]
   {:pre [(s/valid? ::update-session-args session-args)]}
   (swap! session (fn [session]
                    (as-> session $
                      (apply clara/retract $ retract)
                      (clara/insert-all $ insert)
                      (clara/fire-rules $))))))
