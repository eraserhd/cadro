(ns cadro.session
  (:require-macros
   [clara.rules :as clara])
  (:require
   [cadro.model :as model]
   [clara.rules :as clara]
   [clojure.edn :as edn]
   [re-frame.core :as r]))

(clara/defsession empty-session 'cadro.model)

(defonce session (atom
                   (let [ls-tuples (some-> js/localStorage
                                     (.getItem "session")
                                     (edn/read-string))]
                     (-> empty-session
                         (clara/insert-all (map (fn [[e a v]]
                                                  (model/asserted e a v))
                                                ls-tuples))
                         (clara/fire-rules)))))

(defn clear! []
  (reset! session empty-session))

(r/reg-fx
 :session
 (fn [new-session]
   (let [new-session (clara/fire-rules new-session)
         tuples      (->> (clara/query new-session model/persistent-facts)
                          (map (fn [{:keys [?e ?a ?v]}]
                                 [?e ?a ?v])))]
     (reset! session new-session)
     (.setItem js/localStorage "session" (pr-str tuples)))))

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
