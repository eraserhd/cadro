(ns cadro.test-macros
  (:require
   [clojure.test :refer [testing]]))

(defn- rewrite-expr [expr]
  (cond
   (vector? expr)
   (let [[e a v] expr]
     `(clara.rules/insert (cadro.model/asserted ~e ~a ~v)))
  
   :else
   `(-> clara.rules/fire-rules
        ~expr)))

(defn- rewrite-arrows [exprs]
  (cond
   (empty? exprs)
   '()
  
   (= '=> (second exprs))
   (let [[a _ b & more] exprs]
     (cons `(cadro.test/check
             (fn [session#]
               (-> session# ~a))
             ~b)
           (rewrite-arrows more)))
  
   :else
   (let [[h & more] exprs]
     (cons (rewrite-expr h) (rewrite-arrows more)))))
  
(defmacro scenario
  "Threads a session through all exprs, calls fire-rules at appropriate
  points, and adds expr => checker syntax that continue threading the
  session.

  If checker is a fn, (checker result) should return a truthy value.
  Otherwise, (= result checker) is tested."
  [msg & exprs]
  (assert (string? msg))
  `(testing ~msg
     (-> cadro.session/base-session
         ~@(rewrite-arrows exprs))))

;; str                    -> (testing str ...)
;; vector?                -> (insert (asserted []))
;; (f a b ...)            -> (f session a b ...)
;; (f a b ...) => checker -> (do (checker session (f a b ...)) session)
;; identity    => checker
