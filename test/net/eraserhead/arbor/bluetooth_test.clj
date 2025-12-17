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
    (let [devices (-> (bt/device-list-arrived {:db {::bt/devices
                                                    {"00:00:01" {::bt/id "00:00:01"
                                                                 ::bt/name "Foo"
                                                                 ::bt/address "00:00:01"}}}}
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
             (get devices "00:00:02")))))
  (testing "existing device status is preserved"
    (let [devices (-> (bt/device-list-arrived {:db {::bt/devices
                                                    {"00:00:01" {::bt/id "00:00:01"
                                                                 ::bt/name "Foo"
                                                                 ::bt/status :connected
                                                                 ::bt/address "00:00:01"}}}}
                                              [::bt/device-list-arrived
                                               [{::bt/id "00:00:01"
                                                 ::bt/name "Bar"
                                                 ::bt/address "00:00:01"}]])
                      (get-in [:db ::bt/devices]))]
      (is (= :connected (get-in devices ["00:00:01" ::bt/status]))))))

(deftest t-log-event
  (testing "log-event appends events"
    (let [log (-> {}
                  (bt/log-event "00:00:01" "set-status" "connected")
                  (bt/log-event "02:22:22" "received"   "hello, world")
                  (get ::bt/log))]
      (is (= [{::bt/id "00:00:01", ::bt/event-type "set-status", ::bt/event-data "connected"}
              {::bt/id "02:22:22", ::bt/event-type "received", ::bt/event-data "hello, world"}]
             log))))
  (testing "log-event discards more than 100 events"
    (let [log (-> (iterate #(bt/log-event % "00:00:01" "recieved" "1234") {})
                  (nth 405)
                  (get ::bt/log))]
      (is (= 100 (count log))))))

(deftest t-log-received
  (testing "log-received formats data in a nice hex dump"
    (let [data (-> {}
                   (bt/log-received "00:00:01" "Hello, world!  This is a test.")
                   (get-in [::bt/log 0 ::bt/event-data]))]
      (is (= (str "48 65 6c 6c 6f 2c 20 77 6f 72 6c 64 21 20 20 54   Hello, world!  T\n"
                  "68 69 73 20 69 73 20 61 20 74 65 73 74 2e         his is a test.")
             data)))))
