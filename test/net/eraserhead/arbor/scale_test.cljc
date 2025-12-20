(ns net.eraserhead.arbor.scale-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest testing is]]
   [net.eraserhead.arbor.scale :as scale]))

(deftest t-device-list-arrived
  (is s/*compile-asserts*)
  (is (s/check-asserts?)
      "need to be checking specs during test runs")
  (testing "new devices found, and they start disconnected"
    (let [devices (-> (scale/device-list-arrived {:db {}}
                                                 [::scale/device-list-arrived
                                                  [{::scale/id "00:00:01"
                                                    ::scale/name "Foo"
                                                    ::scale/address "00:00:01"}
                                                   {::scale/id "00:00:02"
                                                    ::scale/name "Bar"
                                                    ::scale/address "00:00:02"}]])
                      (get-in [:db ::scale/devices]))]
      (is (= {::scale/id "00:00:01"
              ::scale/name "Foo"
              ::scale/status :disconnected
              ::scale/address "00:00:01"}
             (get devices "00:00:01")))
      (is (= {::scale/id "00:00:02"
              ::scale/name "Bar"
              ::scale/status :disconnected
              ::scale/address "00:00:02"}
             (get devices "00:00:02")))))
  (testing "existing device no longer found"
    (let [devices (-> (scale/device-list-arrived {:db {::scale/devices
                                                       {"00:00:01" {::scale/id "00:00:01"
                                                                    ::scale/name "Foo"
                                                                    ::scale/address "00:00:01"}}}}
                                                 [::scale/device-list-arrived
                                                  [{::scale/id "00:00:02"
                                                    ::scale/name "Bar"
                                                    ::scale/address "00:00:02"}]])
                      (get-in [:db ::scale/devices]))]
      (is (not (contains? devices "00:00:01")))
      (is (= {::scale/id "00:00:02"
              ::scale/name "Bar"
              ::scale/status :disconnected
              ::scale/address "00:00:02"}
             (get devices "00:00:02")))))
  (testing "existing device status is preserved"
    (let [devices (-> (scale/device-list-arrived {:db {::scale/devices
                                                       {"00:00:01" {::scale/id "00:00:01"
                                                                    ::scale/name "Foo"
                                                                    ::scale/status :connected
                                                                    ::scale/address "00:00:01"}}}}
                                                 [::scale/device-list-arrived
                                                  [{::scale/id "00:00:01"
                                                    ::scale/name "Bar"
                                                    ::scale/address "00:00:01"}]])
                      (get-in [:db ::scale/devices]))]
      (is (= :connected (get-in devices ["00:00:01" ::scale/status]))))))

(deftest t-log-event
  (testing "log-event appends events"
    (let [log (-> {}
                  (scale/log-event "00:00:01" "set-status" "connected")
                  (scale/log-event "02:22:22" "received"   "hello, world")
                  (get ::scale/log))]
      (is (= [{::scale/id "00:00:01", ::scale/event-type "set-status", ::scale/event-data "connected"}
              {::scale/id "02:22:22", ::scale/event-type "received", ::scale/event-data "hello, world"}]
             log))))
  (testing "log-event discards more than 100 events"
    (let [log (-> (iterate #(scale/log-event % "00:00:01" "recieved" "1234") {})
                  (nth 405)
                  (get ::scale/log))]
      (is (= 100 (count log))))))

(deftest t-log-received
  (testing "log-received formats data in a nice hex dump"
    (let [data (-> {}
                   (#'scale/log-received "00:00:01" "Hello, world!  This is a test.\n")
                   (get-in [::scale/log 0 ::scale/event-data]))]
      (is (= (str "48 65 6c 6c 6f 2c 20 77 6f 72 6c 64 21 20 20 54   Hello, world!  T\n"
                  "68 69 73 20 69 73 20 61 20 74 65 73 74 2e 0a      his is a test..")
             data)))))

(deftest t-process-recieved
  (testing "parses and stores received axes"
    (let [db (-> {::scale/devices {"00:00:01" {}}}
                 (scale/process-received "00:00:01" "X150;Y250;Z350;T72;\n"))]
      (is (= {::scale/devices {"00:00:01" {::scale/receive-buffer ""
                                           ::scale/axes {"X" 150
                                                         "Y" 250
                                                         "Z" 350
                                                         "T" 72}}}
              ::scale/log [{::scale/id         "00:00:01",
                            ::scale/event-type "received",
                            ::scale/event-data (str "58 31 35 30 3b 59 32 35 30 3b 5a 33 35 30 3b 54   X150;Y250;Z350;T\n"
                                                 "37 32 3b 0a                                       72;.")}]}
             db))))
  (testing "works correctly if data was received in any size chunk"
    (doseq [:let [data "X150;Y250;Z350;T72;\n"]
            i (range (count data))
            :let [part-a (subs data 0 i)
                  part-b (subs data i)]]
      (let [db (-> {::scale/devices {"00:00:01" {}}}
                   (scale/process-received "00:00:01" part-a)
                   (scale/process-received "00:00:01" part-b)
                   (dissoc ::scale/log))]
        (is (= {::scale/devices {"00:00:01" {::scale/receive-buffer ""
                                             ::scale/axes {"X" 150
                                                           "Y" 250
                                                           "Z" 350
                                                           "T" 72}}}}
               db))))))
