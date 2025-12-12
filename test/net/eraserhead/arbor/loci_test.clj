(ns net.eraserhead.arbor.loci-test
 (:require
  [clojure.spec.alpha :as s]
  [clojure.test :refer [deftest is]]
  [net.eraserhead.arbor.loci :as loci]))

(deftest t-empty-db
  (is (s/valid? ::loci/db loci/empty-db))
  (is (nil? (loci/focused loci/empty-db))
      "An empty database should be the only one with no focused locus."))

(deftest t-add-top-level
  (let [id #uuid "dd5e99e7-5d84-4f29-8ba6-dc403aa5021a",
        locus {::loci/id id, ::loci/name "Foo"}
        singlet (loci/add-top-level
                 loci/empty-db
                 {::loci/id id, ::loci/name "Foo"})]
    (is (= id (::loci/id (loci/get singlet id)))
        "Can retrieve stored loci")
    (is (= id (::loci/id (loci/focused singlet)))
        "Added loci is focused"))
  ;; FIXME: adds to top-level query
  ;; FIXME: adding a child will break the next test, since the focus will not be the top level
  ;; FIXME: we really wnat to set ::next, not ::previous
  (testing "adding top-level loci correctly chains ::previous keys"
    (let [id1 #uuid "82681534-7012-47a8-9ff8-963ae53ec957"
          id2 #uuid "feabf208-6095-407e-a07e-0868bb817d54"
          id3 #uuid "e41b40c0-460c-4d24-acd8-3704a9ebf008"
          locus1 {::loci/id id1, ::loci/name "Foo"}
          locus2 {::loci/id id2, ::loci/name "Bar"}
          locus3 {::loci/id id3, ::loci/name "Baz"}
          db (-> loci/empty-db
                 (loci/add-top-level locus1)
                 (loci/add-top-level locus2)
                 (loci/add-top-level locus3))]
      (is (nil? (::loci/id (::loci/previous (loci/get db id1)))))
      (is (= id1 (::loci/id (::loci/previous (loci/get db id2)))))
      (is (= id2 (::loci/id (::loci/previous (loci/get db id3))))))))
