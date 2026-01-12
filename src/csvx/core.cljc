(ns csvx.core
  "Character-separated values parser and transformer that gives user fine-grained
  control. In CLJ returns data directly. In CLJS returns a Promise."
  #?(:clj (:import
            (java.io InputStreamReader BufferedReader FileInputStream))))

#?(:clj (set! *warn-on-reflection* true))

;; Constants
(def ^:private ^:const default-separator ",")
(def ^:private ^:const default-encoding "UTF-8")
(def ^:private ^:const line-separator "\n")

;; Default options
(def ^:private default-opts
  {:encoding default-encoding
   :max-lines-to-read #?(:clj Integer/MAX_VALUE
                         :cljs js/Number.MAX_SAFE_INTEGER)
   :separator default-separator
   :line-tokenizer #(.split #?(:clj ^String (str %) :cljs (str %)) default-separator)
   :line-transformer #(map-indexed hash-map %)})

;; Shared implementation
(defn- accumulate-lines
  "Process a single line: tokenize, transform, and add to accumulator.
  Returns [updated-acc lines-remaining]."
  [lines-remaining line-tokenizer line-transformer acc line]
  (if (seq line)
    (let [trimmed (.trim #?(:clj ^String line :cljs line))]
      [(conj! acc (-> trimmed line-tokenizer line-transformer))
       (dec lines-remaining)])
    [acc lines-remaining]))

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
     "Process array of lines using opts, returning persistent vector of results."
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
     "Read file synchronously in Node.js environment."
     [file-path opts]
     (let [fs (js/require "fs")
           content (.readFileSync fs file-path (clj->js {:encoding (:encoding opts)}))
           lines (.split content line-separator)]
       (process-lines lines opts))))

;; Browser File object implementation
#?(:cljs
   (defn- read-file-object
     "Read a browser File object, returning a Promise."
     [file-obj opts]
     (js/Promise.
      (fn [resolve reject]
        (let [reader (js/FileReader.)]
          (set! (.-onload reader)
                (fn [e]
                  (let [content (.. e -target -result)
                        lines (.split content line-separator)]
                    (resolve (process-lines lines opts)))))
          (set! (.-onerror reader)
                (fn [e]
                  (reject (ex-info "Failed to read file" {:error e}))))
          (.readAsText reader file-obj (:encoding opts)))))))

;; Browser URL/fetch implementation
#?(:cljs
   (defn- read-url
     "Fetch and parse CSV from a URL, returning a Promise."
     [url opts]
     (-> (js/fetch url)
         (.then (fn [response]
                  (if (.-ok response)
                    (.text response)
                    (js/Promise.reject (ex-info "Failed to fetch URL"
                                                 {:status (.-status response)})))))
         (.then (fn [text]
                  (let [lines (.split text line-separator)]
                    (process-lines lines opts)))))))

;; Input detection helpers
#?(:cljs
   (defn- file-object? [x]
     "Check if x is a browser File object (has name, size, and either type or text method)."
     (and (some? x)
          (not (string? x))
          (some? (.-name x))
          (some? (.-size x))
          (or (some? (.-type x))
              (fn? (.-text x))))))

#?(:cljs
   (defn- url? [x]
     "Check if x is an HTTP/HTTPS URL string."
     (and (string? x)
          (or (.startsWith x "http://")
              (.startsWith x "https://")))))

#?(:cljs
   (defn- detect-input-type [input]
     "Detect the type of input: :file-object, :url, :file-path, or :unknown."
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
