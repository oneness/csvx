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

(defn sample-json []
  (tmp-file "json-sample" ".json"
            "{\"user_id\":\"lzlZwIpuSWXEnNS91wxjHw\",\"name\":\"Susan\",\"review_count\":1,\"yelping_since\":\"2015-09-28\",\"friends\":\"None\",\"useful\":0,\"funny\":0,\"cool\":0,\"fans\":0,\"elite\":\"None\",\"average_stars\":2.0,\"compliment_hot\":0,\"compliment_more\":0,\"compliment_profile\":0,\"compliment_cute\":0,\"compliment_list\":0,\"compliment_note\":0,\"compliment_plain\":0,\"compliment_cool\":0,\"compliment_funny\":0,\"compliment_writer\":0,\"compliment_photos\":0}"))

(deftest parse-json-test
  (testing "Given a json file where each line is valid json, parse it into [{}]"
    (is (= [{:elite "None",
            :compliment_cute 0,
            :fans 0,
            :cool 0,
            :compliment_hot 0,
            :compliment_plain 0,
            :compliment_profile 0,
            :funny 0,
            :name "Susan",
            :compliment_more 0,
            :yelping_since "2015-09-28",
            :compliment_list 0,
            :compliment_cool 0,
            :compliment_writer 0,
            :friends "None",
            :useful 0,
            :average_stars 2.0,
            :user_id "lzlZwIpuSWXEnNS91wxjHw",
            :compliment_funny 0,
            :compliment_note 0,
            :review_count 1,
            :compliment_photos 0}]
           (-> (sample-json)
               (readx {:line-tokenizer (fn [line]
                                         (map #(.split ^String % ":")
                                              (-> (clojure.string/replace line #"\{|\}" "")
                                                  (.split ","))))
                       :line-transformer (fn [line]
                                           (reduce (fn [acc [k v]]
                                                     (merge acc
                                                            {(-> k read-string keyword) (read-string v)}))
                                                   {}
                                                   line))}))))))
