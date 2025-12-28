(ns csvx.test-helper
  (:require [cljs.closure :as closure]
            [clojure.java.shell :refer [sh]]))

(defn run-node-tests [& _args]
  (println "Compiling Node tests...")
  (closure/build "test/csvx/node_test.cljs"
                 {:main 'csvx.node-test
                  :output-to "test/out/node_test.js"
                  :output-dir "test/out"
                  :target :nodejs
                  :optimizations :simple})
  (println "Running Node tests...")
  (let [result (sh "node" "test/out/node_test.js")]
    (println (:out result))
    (when (seq (:err result))
      (println (:err result)))
    (System/exit (if (zero? (:exit result)) 0 1))))

(defn run-browser-tests [& _args]
  (println "Compiling Browser tests...")
  (closure/build "test/csvx/browser_test.cljs"
                 {:main 'csvx.browser-test
                  :output-to "test/public/js/browser_test.js"
                  :output-dir "test/public/js/out"
                  :asset-path "js/out"
                  :target :browser
                  :optimizations :none})
  (let [file (java.io.File. "test/public/index.html")
        uri (.toURI file)]
    (println "Opening browser with test results...")
    (.browse (java.awt.Desktop/getDesktop) uri)))
