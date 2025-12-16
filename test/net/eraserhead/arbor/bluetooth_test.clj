(ns net.eraserhead.arbor.bluetooth-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [net.eraserhead.arbor.bluetooth :as bt]))

(deftest t-device-list-arrived
  (testing "new devices found, and they start disconnected"
    (let [devices (-> (bt/device-list-arrived {:db {}}
                                              [::bt/device-list-arrived
                                               [{::bt/id "00:00:01"
                                                 ::bt/name "Foo"
                                                 ::bt/address "00:00:01"}
                                                {::bt/id "00:00:02"
                                                 ::bt/name "Bar"
                                                 ::bt/address "00:00:02"}]])
                      (get-in [:db ::bt/devices]))]
      (is (= {::bt/id "00:00:01"
              ::bt/name "Foo"
              ::bt/status :disconnected
              ::bt/address "00:00:01"}
             (get devices "00:00:01")))
      (is (= {::bt/id "00:00:02"
              ::bt/name "Bar"
              ::bt/status :disconnected
              ::bt/address "00:00:02"}
             (get devices "00:00:02")))))
  (testing "existing device no longer found"
    (let [devices (-> (bt/device-list-arrived {:db {"00:00:01" {::bt/id "00:00:01"
                                                                ::bt/name "Foo"
                                                                ::bt/address "00:00:01"}}}
                                              [::bt/device-list-arrived
                                               [{::bt/id "00:00:02"
                                                 ::bt/name "Bar"
                                                 ::bt/address "00:00:02"}]])
                      (get-in [:db ::bt/devices]))]
      (is (not (contains? devices "00:00:01")))
      (is (= {::bt/id "00:00:02"
              ::bt/name "Bar"
              ::bt/status :disconnected
              ::bt/address "00:00:02"}
             (get devices "00:00:02"))))))
