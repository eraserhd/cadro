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
                   (bt/log-received "00:00:01" "Hello, world!  This is a test.\n")
                   (get-in [::bt/log 0 ::bt/event-data]))]
      (is (= (str "48 65 6c 6c 6f 2c 20 77 6f 72 6c 64 21 20 20 54   Hello, world!  T\n"
                  "68 69 73 20 69 73 20 61 20 74 65 73 74 2e 0a      his is a test..")
             data)))))

(deftest t-process-recieved
  (testing "parses and stores received axes"
    (let [db (-> {::bt/devices {"00:00:01" {}}}
                 (bt/process-received "00:00:01" "X150;Y250;Z350;T72;\n"))]
      (is (= {::bt/devices {"00:00:01" {::bt/receive-buffer ""
                                        ::bt/axes {"X" 150
                                                   "Y" 250
                                                   "Z" 350
                                                   "T" 72}}}
              ::bt/log [{::bt/id         "00:00:01",
                         ::bt/event-type "received",
                         ::bt/event-data (str "58 31 35 30 3b 59 32 35 30 3b 5a 33 35 30 3b 54   X150;Y250;Z350;T\n"
                                              "37 32 3b 0a                                       72;.")}]}
             db))))
  (testing "works correctly if data was received in any size chunk"
    (doseq [:let [data "X150;Y250;Z350;T72;\n"]
            i (range (count data))
            :let [part-a (subs data 0 i)
                  part-b (subs data i)]]
      (let [db (-> {::bt/devices {"00:00:01" {}}}
                   (bt/process-received "00:00:01" part-a)
                   (bt/process-received "00:00:01" part-b)
                   (dissoc ::bt/log))]
        (is (= {::bt/devices {"00:00:01" {::bt/receive-buffer ""
                                          ::bt/axes {"X" 150
                                                     "Y" 250
                                                     "Z" 350
                                                     "T" 72}}}}
               db))))))
    
