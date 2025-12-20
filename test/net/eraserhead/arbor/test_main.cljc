(ns net.eraserhead.arbor.test-main
  (:require
   [clojure.spec.alpha :as s]
   [shadow.test.node]))

(defn main [& args]
  (s/check-asserts true)
  (apply shadow.test.node/main args))
