(ns csvx.core-test
  (:require [clojure.test :refer :all]
            [csvx.core :refer :all])
  (:import (java.io File)))

(defn tmp-file [prefix suffix content]
  (let [f (doto
              (File/createTempFile prefix suffix)
            (.deleteOnExit))
        path (.getPath f)]
    (spit path content)
    path))

(defn sample-csv []
  (tmp-file "xcsv-sample" ".csv"
            "name,age,gender
             John,32,M

             Susan,28,F"))

(deftest parse-csv-test
  (testing "Given sample files, we can parse into vector of [{col-idx, col-value}]"
    (is (= [[{0 "name"} {1 "age"} {2 "gender"}]
            [{0 "John"} {1 "32"} {2 "M"}]
            [{0 "Susan"} {1 "28"} {2 "F"}]]
           (-> (sample-csv) readx)))))
