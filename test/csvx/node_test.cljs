(ns csvx.node-test
  "Tests for CLJS Node.js environment"
  (:require [cljs.test :refer-macros [deftest is testing async run-tests]]
            [csvx.core :as csvx]
            [cljs.nodejs :as nodejs]))

;; Enable console output for cljs.test
(set! *print-fn* js/console.log)
(set! *print-err-fn* js/console.error)

(nodejs/enable-util-print!)

(def ^:private fs (js/require "fs"))
(def ^:private os (js/require "os"))

(defn tmp-file [prefix suffix content]
  (let [tmp-dir (.tmpdir os)
        file-path (str tmp-dir "/" prefix (js/Date.now) suffix)]
    (.writeFileSync fs file-path content)
    file-path))

(defn sample-csv []
  (let [tmp-dir (.tmpdir os)
        file-path (str tmp-dir "/xcsv-sample-" (js/Date.now) ".csv")
        content "name,age,gender
John,32,M

Susan,28,F"]
    (.writeFileSync fs file-path content)
    file-path))

(deftest parse-csv-test
  (testing "Given sample files, we can parse into vector of [{col-idx, col-value}]"
    (async done
      (-> (csvx/readx (sample-csv))
          (.then (fn [data]
                   (is (= [[{0 "name"} {1 "age"} {2 "gender"}]
                           [{0 "John"} {1 "32"} {2 "M"}]
                           [{0 "Susan"} {1 "28"} {2 "F"}]]
                          data))
                   (done)))
          (.catch (fn [err]
                    (js/console.error "Test failed:" err)
                    (done)))))))

(deftest max-lines-test
  (testing "max-lines-to-read option limits the number of lines read"
    (async done
      (-> (csvx/readx (sample-csv) {:max-lines-to-read 2})
          (.then (fn [data]
                   (is (= 2 (count data)))
                   (done)))
          (.catch (fn [err]
                    (js/console.error "Test failed:" err)
                    (done)))))))

(defn ^:export -main [& args]
  (js/console.log "Testing csvx.node-test")
  (run-tests 'csvx.node-test))

(set! *main-cli-fn* -main)
