(ns cadro.ui.hiccup-test
  (:require
   [cadro.ui.hiccup :as h]
   [clojure.test :refer [deftest testing are]]))

(deftest t-wrap-content
  (testing "merging props"
    (are [expect props input] (= expect (h/wrap-content props input))
      [:a {:b "f"} [:c]]         {}       [:a {:b "f"} [:c]]
      [:a {:b "f", :d 42} [:c]]  {:d 42}  [:a {:b "f"} [:c]])))
