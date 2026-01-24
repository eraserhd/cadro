(ns cadro.session
  (:require-macros
   [clara.rules :as clara])
  (:require
   [cadro.model :as model]
   [clara.rules :as clara]
   [clojure.edn :as edn]
   [net.eraserhead.clara-eql.pull]
   [reagent.ratom]
   [re-frame.core :as r]))

(clara/defsession empty-session 'cadro.model 'net.eraserhead.clara-eql.pull)

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

(defn- save-session!
  "Stores session to localStorage."
  [session]
  (let [data (->> (clara/query session model/persistent-facts)
                  (map (fn [{:keys [?e ?a ?v]}]
                         [?e ?a ?v]))
                  pr-str)]
    (.setItem js/localStorage "session" data)))

(r/reg-fx
 :session
 (fn [new-session]
   (let [new-session (clara/fire-rules new-session)]
     (if-let [errors (seq (model/errors new-session))]
       (js/console.warn "Session not saved due to errors:" errors)
       (do
         (reset! session new-session)
         (save-session! new-session))))))

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

(r/reg-sub-raw
 :session
 (fn [_ _]
   (reagent.ratom/make-reaction #(deref session))))
