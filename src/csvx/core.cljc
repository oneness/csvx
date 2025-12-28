(ns csvx.core
  "Character-separated values parser and transformer that gives user fine-grained
  control. In CLJ returns data directly. In CLJS returns a Promise."
  #?(:clj (:import
            (java.io Closeable InputStreamReader BufferedReader FileInputStream))))

#?(:clj (set! *warn-on-reflection* true))

;; Default options
(def ^:private default-opts
  {:encoding "UTF-8"
   :max-lines-to-read #?(:clj Integer/MAX_VALUE
                         :cljs js/Number.MAX_SAFE_INTEGER)
   :line-tokenizer #(.split ^String (str %) ",")
   :line-transformer #(map-indexed hash-map %)})

;; Shared implementation
#?(:clj
   (defn- accumulate-lines
     [lines-remaining line-tokenizer line-transformer acc line]
     (if (seq line)
       (let [trimmed (.trim ^String line)]
         [(conj! acc (-> trimmed line-tokenizer line-transformer))
          (dec lines-remaining)])
       [acc lines-remaining])))

#?(:cljs
   (defn- accumulate-lines
     [lines-remaining line-tokenizer line-transformer acc line]
     (if (seq line)
       (let [trimmed (.trim line)]
         [(conj! acc (-> trimmed line-tokenizer line-transformer))
          (dec lines-remaining)])
       [acc lines-remaining])))

#?(:clj
   (defn- read-lines-from-file
     [^String file-path encoding opts]
     (with-open [reader (BufferedReader.
                         (InputStreamReader.
                          (FileInputStream. file-path)
                          ^String encoding))]
       (loop [line (.readLine reader)
              result (transient [])
              lines-remaining (:max-lines-to-read opts)]
         (if (or (nil? line) (zero? lines-remaining))
           (persistent! result)
           (let [[new-result new-remaining]
                 (accumulate-lines lines-remaining
                                  (:line-tokenizer opts)
                                  (:line-transformer opts)
                                  result
                                  line)]
             (recur (.readLine reader) new-result new-remaining)))))))

#?(:clj
   (defn readx
     "Read CSV file from file path. Returns data directly.

     Options:
       :encoding - File encoding (default: \"UTF-8\")
       :max-lines-to-read - Maximum number of lines to read (default: Integer/MAX_VALUE)
       :line-tokenizer - Function to split line into fields (default: comma split)
       :line-transformer - Function to transform tokenized line (default: map-indexed hash-map)"
     [file-path & [opts]]
     (let [opts (merge default-opts opts)]
       (read-lines-from-file file-path (:encoding opts) opts))))

;; CLJS Implementation
#?(:cljs
   (defn- process-lines
     [lines opts]
     (loop [result (transient [])
            idx 0
            lines-remaining (:max-lines-to-read opts)]
       (if (or (>= idx (.-length lines)) (zero? lines-remaining))
         (persistent! result)
         (let [[new-result new-remaining]
               (accumulate-lines lines-remaining
                                (:line-tokenizer opts)
                                (:line-transformer opts)
                                result
                                (aget lines idx))]
           (recur new-result (inc idx) new-remaining))))))

;; Node.js implementation
#?(:cljs
   (defn- read-node-sync
     [file-path opts]
     (let [fs (js/require "fs")
           content (.readFileSync fs file-path (clj->js {:encoding (:encoding opts)}))
           lines (.split content "\n")]
       (process-lines lines opts))))

;; Browser File object implementation
#?(:cljs
   (defn- read-file-object
     [file-obj opts]
     (js/Promise.
      (fn [resolve reject]
        (let [reader (js/FileReader.)]
          (set! (.-onload reader)
                (fn [e]
                  (let [content (.. e -target -result)
                        lines (.split content "\n")]
                    (resolve (process-lines lines opts)))))
          (set! (.-onerror reader)
                (fn [e]
                  (reject (ex-info "Failed to read file" {:error e}))))
          (.readAsText reader file-obj (:encoding opts)))))))

;; Browser URL/fetch implementation
#?(:cljs
   (defn- read-url
     [url opts]
     (-> (js/fetch url)
         (.then (fn [response]
                  (if (.-ok response)
                    (.text response)
                    (js/Promise.reject (ex-info "Failed to fetch URL"
                                                 {:status (.-status response)})))))
         (.then (fn [text]
                  (let [lines (.split text "\n")]
                    (process-lines lines opts)))))))

;; Input detection helpers
#?(:cljs
   (defn- file-object? [x]
     (and (some? x)
          (not (string? x))
          (some? (.-name x))
          (some? (.-size x))
          (or (some? (.-type x))
              (fn? (.-text x))))))

#?(:cljs
   (defn- url? [x]
     (and (string? x)
          (or (.startsWith x "http://")
              (.startsWith x "https://")))))

#?(:cljs
   (defn- detect-input-type [input]
     (cond
       (file-object? input) :file-object
       (url? input)         :url
       (string? input)      :file-path
       :else                :unknown)))

#?(:cljs
   (defn readx
     "Read CSV file from various sources. Returns a Promise.

     Supports:
       - File objects from <input type=\"file\">
       - URLs (http:// or https://) via fetch
       - File paths (Node.js only)

     Options:
       :encoding - File encoding (default: \"UTF-8\")
       :max-lines-to-read - Maximum number of lines to read (default: Number.MAX_SAFE_INTEGER)
       :line-tokenizer - Function to split line into fields (default: comma split)
       :line-transformer - Function to transform tokenized line (default: map-indexed hash-map)

     Examples:
       ;; From file input
       (-> (readx file-object)
           (.then (fn [data] (console.log data))))

       ;; From URL
       (-> (readx \"https://example.com/data.csv\")
           (.then (fn [data] (console.log data))))

       ;; From file path (Node.js only)
       (readx \"/path/to/file.csv\")"
     [input & [opts]]
     (let [opts (merge default-opts opts)
           input-type (detect-input-type input)]

       (case input-type
         :file-object (read-file-object input opts)

         :url (read-url input opts)

         :file-path
         (if-some [require js/require]
           (js/Promise.resolve (read-node-sync input opts))
           (js/Promise.reject
            (ex-info "Cannot read file paths directly in browser. Use a File object or URL."
                     {:input input})))

         :unknown
         (js/Promise.reject
          (ex-info "Unrecognized input type. Expected File object, URL string, or file path."
                   {:input input}))))))
