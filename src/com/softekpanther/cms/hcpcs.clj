(ns com.softekpanther.cms.hcpcs
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.pprint :refer [pprint]]
            [dk.ative.docjure.spreadsheet :as ss])
  (:import [org.apache.poi.ss.util CellReference]
           [org.apache.poi.ss.usermodel Sheet Row])
  (:gen-class))

(def rekey-2020
 {"HCPCS Code"             :HCPCSCode
  "Short Descriptor"       :ShortDescriptor
  "SI"                     :StatusIndicator
  "Payment Rate"           :PaymentRate
  "* Indicates a Change"   :Changed?})

(defrecord ScheduleBEntry
  [^String   HCPCSCode
   ^String   ShortDescriptor
   ^String   StatusIndicator
   ^Double   PaymentRate
   ^Boolean  Changed?])

(defn rekey
  [row]
  (-> (set/rename-keys row rekey-2020)
      (update :Changed? boolean)))

(defn escape-csv
  [x]
  (cond (and (string? x) (re-seq #"|[0-9,'\" ]" x))    (str \" (string/replace x #"\"" "\"\"") \")
        (some? x)                                     (str x)))
        

(defn sheet->ScheduleBEntry-seq
  [sheet]
  (let [relevant-rows (->> (ss/row-seq sheet)
                           (map (fn [row] (map
                                            #(let [x (ss/read-cell %)]
                                               (if (string? x)
                                                  (string/trim x)
                                                  x))
                                             (ss/cell-seq row))))
                           (drop-while
                              (fn drop-header? [cells]
                                  (some? (first cells))))
                           (filter #(>= (count %) 9)))
        [header & value-rows] relevant-rows]
    (->> value-rows
         (map (fn [cells]
                 (-> (into {} (map vector header cells))
                     (select-keys (keys rekey-2020))
                     rekey
                     map->ScheduleBEntry))))))
(defn print-CSV
  [records]
  (println (string/join "," (map name (keys (map->ScheduleBEntry {})))))
  (doseq [row records]
    (println (string/join "," (map escape-csv (vals row))))))

(def print-JSON json/pprint)

(comment
  (def ex2020-header  ["HCPCS Code"
                       "Short Descriptor"
                       "SI"
                       "APC"
                       "Relative Weight"
                       "Payment Rate"
                       "National Unadjusted Copayment"
                       "Minimum Unadjusted Copayment"
                       "Note: Actual copayments would be lower due to the cap on copayments at the Inpatient Deductible of $1,408.00"
                       "* Indicates a Change"])
  (def example-path "January 2020 Addendum B CORRECTION.02042020.xlsx")
  (def workbook (ss/load-workbook example-path))
  (def sheet (first (ss/sheet-seq workbook)))
  (print-CSV (sheet->ScheduleBEntry-seq sheet))
  (print-JSON (sheet->ScheduleBEntry-seq sheet))
  (do))

(def printers
  {"JSON"       print-JSON
   "CSV"        print-CSV})

(defn -main
  [format xlsx-path]
  (let [format (string/upper-case (or (and (string/blank? format) "JSON") format))
        printer (or (get printers format)
                    (do (println "Unsupported format:" format) prn))
        xlsx-path xlsx-path
        rows (->> xlsx-path
                  ss/load-workbook
                  ss/sheet-seq
                  first
                  sheet->ScheduleBEntry-seq)]
    (printer rows)

    ; Tag use report -> stderr
    (binding [*out* *err*]
      (println ";" (count rows) "HCPS Codes:"))))
