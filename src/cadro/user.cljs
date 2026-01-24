(ns ^:dev/always cadro.user
  (:require
   [cadro.db :as db]
   [cadro.model :as model]
   [cadro.session :as session]
   [cadro.test :as t]
   [clara.rules :as clara]
   [clojure.spec.alpha :as s]
   [cljs.repl :refer [dir doc apropos source]]
   [datascript.core :as d]
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

(defn reference []
  (model/reference (session)))
