(ns csvx.core
  "Char(s) seperated values parser and transformer that gives user fine
  grained control"
  (:require
   [clojure.stacktrace :as strace])
  (:import
   (java.io Closeable InputStreamReader BufferedReader FileInputStream)))

(set! *warn-on-reflection* true)

(def ^:private default-opts
  {:encoding "UTF-8"
   :max-lines-to-read Integer/MAX_VALUE
   :line-tokenizer (fn [^String line]
                     (try (.split line ",")
                          (catch Exception e
                            (println :malformed-line line)
                            (strace/print-stack-trace e))))
   :line-transformer #(map-indexed hash-map %)})

(defn- close [ closeable & closables]
  (doall (->> (cons closeable closables)
              (map (fn [c] (when c (.close ^Closeable c)))))))

(defn readx [^String file-path & [opts]]
  "Intended to be used to read in large files."
  (let [options (merge default-opts opts)
        encoding (or ^String (:encoding options)
                     "UTF-8")
        lines-to-read (atom  (:max-lines-to-read options))
        line-tokenizer (:line-tokenizer options)
        line-transformer (:line-transformer options)
        br (-> file-path FileInputStream. (InputStreamReader. encoding)
               BufferedReader.)
        accumulate-lines (fn [acc line]
                           (if (seq line)
                             (let [transformed (-> (.trim ^String line)
                                                   line-tokenizer
                                                   line-transformer)]
                               (swap! lines-to-read dec)
                               (conj! acc transformed))
                             acc))]
    (loop [line (.readLine br)
           result (transient [])]
      (if (or (nil? line) (zero? @lines-to-read))
        (do (close br)
            (persistent! result))
        (recur (.readLine br)
               (accumulate-lines result line))))))
