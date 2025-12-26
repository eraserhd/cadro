(ns cadro.ui.hiccup-test
  (:require
   [cadro.ui.hiccup :as h]
   [clojure.test :refer [deftest testing are]]))

(deftest t-normalize
  (are [input expect] (= expect (h/normalize input))
      [:a]              #_=> [:a {}]
      [:a {}]           #_=> [:a {}]
      [:a [:b]]         #_=> [:a {} [:b]]
      [:a {:d :f} "t"]  #_=> [:a {:d :f} "t"]))

(deftest t-wrap-content
  (testing "merging props"
    (are [expect props input] (= expect (h/wrap-content props input))
      [:a {:b "f"} [:c]]         {}       [:a {:b "f"} [:c]]
      [:a {:b "f", :d 42} [:c]]  {:d 42}  [:a {:b "f"} [:c]]
      [:a {:b "q"} [:c]]         {:b "q"} [:a {:b "f"} [:c]])))
      ;[:a {:b "q"} [:c]]         {:b "q"} [:a [:c]])))
