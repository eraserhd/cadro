(ns net.eraserhead.arbor.loci-test
 (:require
  [clojure.spec.alpha :as s]
  [clojure.test :refer [deftest testing is]]
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
        "Added loci is focused")))
  ;; FIXME: adds to top-level query
  ;; FIXME: adding a child will break the next test, since the focus will not be the top level
  ;; FIXME: we really wnat to set ::next, not ::previous
