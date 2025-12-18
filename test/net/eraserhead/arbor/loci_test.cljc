(ns net.eraserhead.arbor.loci-test
 (:require
  [clojure.spec.alpha :as s]
  [clojure.test :refer [deftest testing is]]
  [net.eraserhead.arbor.loci :as loci]))

(deftest t-empty-db
  (is (s/valid? ::loci/db loci/empty-db))
  (is (nil? (loci/origin loci/empty-db))
      "An empty database should be the only one with no origin locus."))

(deftest t-conj
  (testing "adding first loci"
    (let [id #uuid "dd5e99e7-5d84-4f29-8ba6-dc403aa5021a",
          locus {::loci/id id, ::loci/parent nil, ::loci/name "Foo"}
          singlet (loci/conj loci/empty-db locus)]
      (is (= id (::loci/id (loci/get singlet id)))
          "can retrieve stored loci")
      (is (= id (::loci/id (loci/origin singlet)))
          "added loci is the origin")
      (is (= [id] (map ::loci/id (loci/top-level singlet)))
          "it appears as top-level query")))
  (testing "adding subsequent top-level loci"
    (let [id1 #uuid "dd5e99e7-5d84-4f29-8ba6-dc403aa5021a",
          id2 #uuid "36bfbea5-9a6b-47d9-8d92-d3a555ee2410"
          locus1 {::loci/id id1, ::loci/parent nil, ::loci/name "Foo"}
          locus2 {::loci/id id2, ::loci/parent nil, ::loci/name "Bar"}
          db (-> loci/empty-db
               (loci/conj locus1)
               (loci/conj locus2))]
      (is (= {::loci/id id1
              ::loci/parent nil
              ::loci/name "Foo"}
             (loci/get db id1))
          "locus1 is stored")
      (is (= {::loci/id id2
              ::loci/parent nil
              ::loci/name "Bar"}
             (loci/get db id2))
          "locus2 is stored")
      (is (= id1 (::loci/id (loci/origin db)))
          "origin did not change after the first conj")
      (is (= #{id1 id2} (into #{} (map ::loci/id) (loci/top-level db)))
          "all added loci are in the top-level query")))
  (testing "adding child loci"
    (let [id1 #uuid "dd5e99e7-5d84-4f29-8ba6-dc403aa5021a",
          id2 #uuid "36bfbea5-9a6b-47d9-8d92-d3a555ee2410"
          locus1 {::loci/id id1, ::loci/parent nil, ::loci/name "Foo"}
          locus2 {::loci/id id2, ::loci/parent id1, ::loci/name "Bar"}
          db (-> loci/empty-db
               (loci/conj locus1)
               (loci/conj locus2))]
      (is (= {::loci/id id1
              ::loci/parent nil
              ::loci/name "Foo"}
             (loci/get db id1))
          "locus1 is stored")
      (is (= {::loci/id id2
              ::loci/parent id1
              ::loci/name "Bar"}
             (loci/get db id2))
          "locus2 is stored")
      (is (= id1 (::loci/id (loci/origin db)))
          "origin did not change after the first conj")
      (is (= #{id1} (into #{} (map ::loci/id) (loci/top-level db)))
          "child loci are in the top-level query"))))

(deftest t-tree
  (let [id1 #uuid "dd5e99e7-5d84-4f29-8ba6-dc403aa5021a",
        id2 #uuid "36bfbea5-9a6b-47d9-8d92-d3a555ee2410"
        locus1 {::loci/id id1, ::loci/parent nil, ::loci/name "Foo"}
        locus2 {::loci/id id2, ::loci/parent id1, ::loci/name "Bar"}
        db (-> loci/empty-db
             (loci/conj locus1)
             (loci/conj locus2))]
    (is (= [{::loci/id id1
             ::loci/name "Foo"
             ::loci/parent nil
             ::loci/origin? true
             ::loci/children
             [{::loci/id id2
               ::loci/name "Bar"
               ::loci/parent id1
               ::loci/origin? false
               ::loci/children []}]}]
           (loci/tree db)))))
