(ns csvx.browser-test
  "Tests for CLJS Browser environment"
  (:require [cljs.test :as t]
            [cljs.test :refer-macros [deftest is testing async run-tests]]
            [csvx.core :as csvx]))

;; Override print functions to use console
(set! *print-fn* js/console.log)
(set! *print-err-fn* js/console.error)

;; Helper to create a mock File object for testing
(defn create-mock-file [filename content]
  (js/File. #js [content] filename #js {:type "text/plain"}))

(def sample-csv-content
  "name,age,gender
John,32,M

Susan,28,F")

(deftest parse-csv-from-file-test
  (testing "Given a File object, we can parse CSV into vector of [{col-idx, col-value}]"
    (async done
      (let [file (create-mock-file "sample.csv" sample-csv-content)]
        (-> (csvx/readx file)
            (.then (fn [data]
                     (is (= [[{0 "name"} {1 "age"} {2 "gender"}]
                             [{0 "John"} {1 "32"} {2 "M"}]
                             [{0 "Susan"} {1 "28"} {2 "F"}]]
                            data))
                     (done)))
            (.catch (fn [err]
                      (js/console.error "Test failed:" err)
                      (done))))))))

(deftest max-lines-from-file-test
  (testing "max-lines-to-read option limits the number of lines read from File"
    (async done
      (let [file (create-mock-file "sample.csv" sample-csv-content)]
        (-> (csvx/readx file {:max-lines-to-read 2})
            (.then (fn [data]
                     (is (= 2 (count data)))
                     (done)))
            (.catch (fn [err]
                      (js/console.error "Test failed:" err)
                      (done))))))))

;; Run all tests using cljs.test
(defn ^:export run-all-tests []
  (run-tests))

(defn ^:export -main []
  (run-all-tests))
