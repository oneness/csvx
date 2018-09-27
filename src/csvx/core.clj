(ns csvx.core
  "Char(s) seperated values parser and transformer that gives user fine
  grained control"
  (:require
   [clojure.stacktrace :as strace])
  (:import
   (java.io Closeable InputStreamReader BufferedReader FileInputStream)))

(set! *warn-on-reflection* true)

(defn- line-tokenizer-ex-handler [^Exception e line]
  (println :malformed-csv-line line)
  (strace/print-stack-trace e))

(defn- line-tokenizer [line & [{:keys [field-separator
                                       line-tokenizer-ex-handler]}]]
  (when (seq line)
    (try
      (.split ^String line field-separator)
      (catch Exception e
        (line-tokenizer-ex-handler e line)
        nil))))

(defn- line-transformer [line {:keys [line-tokenizer] :as opts}]
  (->> (line-tokenizer line opts)
       (map-indexed hash-map)))

(def ^:private default-opts
  {:field-separator ","
   :max-lines-to-read Integer/MAX_VALUE
   :line-tokenizer line-tokenizer
   :line-transformer line-transformer
   :line-tokenizer-ex-handler line-tokenizer-ex-handler
   :encoding "UTF-8"})

(defn- close [closables]
  (doall (map (fn [c] (when c (.close ^Closeable c)))
              closables)))

(defn readx [file-path & [opts]]
  "Intended to be used to read in large files."
  (let [options (merge default-opts opts)
        lines-to-read (atom  (:max-lines-to-read options))
        line-separator (:line-separator options)
        line-transformer (:line-transformer options)
        fis (FileInputStream. ^String file-path)
        isr (InputStreamReader. ^FileInputStream fis
                                (or ^String (:encoding options) "UTF-8"))
        br (BufferedReader. ^InputStreamReader isr)
        accumulate-lines (fn [line acc]
                           (if (seq line)
                             (do (swap! lines-to-read dec)
                                 (->> (line-transformer (.trim ^String line) options)
                                      (conj! acc)))
                             acc))]
    (loop [line (.readLine ^BufferedReader br)
           result (transient [])]
      (if (or (nil? line) (zero? @lines-to-read))
        (do (close [fis isr br])
            (-> (accumulate-lines line result)
                persistent!))
        (recur (.readLine ^BufferedReader br)
               (accumulate-lines line result))))))
