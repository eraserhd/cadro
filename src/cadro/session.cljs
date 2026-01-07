(ns cadro.session
  (:require-macros
   [clara.rules :as clara])
  (:require
   [clara.rules :as clara]
   [re-frame.core :as r]))

(clara/defsession empty-session)

(defonce session (atom empty-session))

(r/reg-fx
 :session
 (fn [new-session]
   (reset! session (clara/fire-rules new-session))))

(r/reg-cofx
 :session
 (fn [coeffects _]
   (assoc coeffects :session @session)))

(defn reg-event
  ([event-name interceptors handler]
   (r/reg-event-fx
     event-name
     (into [] (concat [(r/inject-cofx :session)] interceptors))
     (fn [{:keys [session]} signal]
       {:session (handler session signal)})))
  ([event-name handler]
   (reg-event event-name [] handler)))
