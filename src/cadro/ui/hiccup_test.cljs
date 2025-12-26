(ns cadro.ui.hiccup-test
  (:require
   [cadro.ui.hiccup :as h]
   [clojure.test :refer [deftest testing are]]))

(deftest t-normalize1
  (are [input expect] (= expect (h/normalize1 input))
      [:a]              #_=> [:a {}]
      [:a {}]           #_=> [:a {}]
      [:a [:b]]         #_=> [:a {} [:b]]
      [:a {:d :f} "t"]  #_=> [:a {:d :f} "t"]))

(deftest t-wrap-content
  (testing "merging props"
    (are [input props expect] (= expect (h/wrap-content props input))
      [:a {:b "f"} [:c]] {}       #_=> [:a {:b "f"} [:c]]
      [:a {:b "f"} [:c]] {:d 42}  #_=> [:a {:b "f", :d 42} [:c]]
      [:a {:b "f"} [:c]] {:b "q"} #_=> [:a {:b "q"} [:c]]
      [:a [:c]]          {:b "q"} #_=> [:a {:b "q"} [:c]])))
