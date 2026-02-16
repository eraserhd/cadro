(ns ^:dev/always cadro.user
  (:require
   [cadro.bluetooth :refer [bt-set bt-status]]
   [cadro.model :as model]
   [cadro.model.facts :as facts]
   [cadro.session :as session]
   [clara.rules :as clara]
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [cljs.repl :refer [dir doc apropos source]]
   [net.eraserhead.clara-eql.pull :as pull]))

(defn session
  "Current Clara Rules session."
  []
  @session/session)

(defn query
  [& args]
  (apply clara/query (session) args))

(defn eav-map
  "Full map of EAV triples."
  []
  (:?eav-map (first (clara/query (session) pull/eav-map))))

(defn entid
  "Resolve an entid."
  [entity-ref]
  (pull/entid (session) entity-ref))

(defn pull
  "Pull data from Clara session."
  [selector eid]
  (pull/pull (session) selector eid))

(defn upsert [e a v]
  (swap! session/session facts/upsert e a v))
