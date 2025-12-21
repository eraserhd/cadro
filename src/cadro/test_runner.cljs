(ns cadro.test-runner
  {:dev/always true}
  (:require
   [clojure.spec.alpha :as s]
   [shadow.test.browser :as sb]))

(s/check-asserts true)

(defn start []
  (sb/start))

(def stop sb/stop)
(def init sb/init)
