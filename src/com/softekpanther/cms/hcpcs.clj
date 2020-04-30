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

(defn- rekey
  [row]
  (-> (set/rename-keys row rekey-2020)
      (update :Changed? boolean)
      (update :HCPCSCode str)))

(defn- escape-csv
  [x]
  (cond (and (string? x) (re-seq #"|[0-9,'\" ]" x))    (str \" (string/replace x #"\"" "\"\"") \")
        (some? x)                                     (str x)))

(defn- sheet->ScheduleBEntry-seq
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
                                  ;(println (first cells) :drop? (some? (first cells)))
                                  (or (string/blank? (str (first cells))))))
                           (filter #(>= (count %) 3)))
        required-headers (into #{} (map str (keys rekey-2020)))
        [header & value-rows] relevant-rows]
    (when-not (every? (into #{} header) required-headers)
      (binding [*out* *err*]
        (println "Missing some required fields.")
        (println "Required: " (string/join "," (keys rekey-2020)))
        (println "Actual:   " header)
        (println "Missing:  " (remove (into #{} header) (seq required-headers)))))
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

(defn- sql-literal
  [x]
  (cond (string? x)     (str \' (string/replace x #"\'" "''") \')
        (true? x)       "1"
        (false? x)      "0"
        (some? x)       (str x)
        (nil? x)        "NULL"))

(defn print-SQL
  [records]
  (let [varchar-lengths
        {:HCPCSCode (apply max (map (comp count str :HCPCSCode) records))
         :ShortDescriptor (apply max (map (comp count str :ShortDescriptor) records))
         :StatusIndicator (apply max (map (comp count str :StatusIndicator) records))}
        any-nils?
        {:HCPCSCode (some (comp nil? :HCPCSCode) records)
         :ShortDescriptor (some (comp nil? :ShortDescriptor) records)
         :StatusIndicator (some (comp nil? :StatusIndicator) records)}]
    (println "DECLARE @ScheduleB TABLE  (")
    (println "  HCPCSCode varchar(" (get varchar-lengths :HCPCSCode) ") "(if (any-nils? :HCPCSCode) "" "NOT") " NULL PRIMARY KEY,")
    (println "  ShortDescriptor varchar(" (get varchar-lengths :ShortDescriptor) ") "(if (any-nils? :ShortDescriptor) "" "NOT") " NULL,")
    (println "  StatusIndicator varchar(" (get varchar-lengths :StatusIndicator) ") "(if (any-nils? :StatusIndicator) "" "NOT") " NULL,")
    (println "  PaymentRate REAL NULL,")
    (println "  Changed bit NOT NULL);")
    (doseq [record records]
      (println
        (str
          "INSERT @ScheduleB VALUES ("
          (sql-literal (.-HCPCSCode record))           ","
          (sql-literal (.-ShortDescriptor record))     ","
          (sql-literal (.-StatusIndicator record))     ","
          (sql-literal (.-PaymentRate record))         ","
          (sql-literal (.-Changed? record))            " "
          ");")))))

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
   "CSV"        print-CSV
   "SQL"        print-SQL})

(defn load-records
  [xlsx-path]
  (->> xlsx-path
       ss/load-workbook
       ss/sheet-seq
       first
       sheet->ScheduleBEntry-seq))

(defn -main
  [format & xlsx-paths]
 (if (empty? xlsx-paths)
  (println "Arguments: <format> my1.xlsx [... myN.xlsx]\r\nwhere format is one of CSV, JSON, or SQL.")
  (let [format (string/upper-case (or (and (string/blank? format) "JSON") format))
        printer (or (get printers format)
                    (do (println "Unsupported format:" format) prn))
        records (->> (map load-records xlsx-paths)
                     (map (fn [records] (zipmap (map #(.-HCPCSCode %) records) records)))
                     (apply merge {})
                     vals
                     (sort-by #(.-HCPCSCode %)))]
    (printer records)
    (println)

    ; Write count to stderr
    (binding [*out* *err*]
      (println "; loadedf/merged" (count records) "HCPS Codes from" (pr-str xlsx-paths))))))
