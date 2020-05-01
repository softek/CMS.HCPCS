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
   ^Boolean  Changed?
   ^String   Source])

(defn- rekey
  [row]
  (-> (set/rename-keys row rekey-2020)
      (update :Changed? boolean)
      (update :HCPCSCode str)))

(defn- escape-csv
  [x]
  (cond (and (string? x) (re-seq #"|[0-9,'\" ]" x))    (str \" (string/replace x #"\"" "\"\"") \")
        (some? x)                                      (str x)))

(defn- sheet->ScheduleBEntry-seq
 ([sheet] (sheet->ScheduleBEntry-seq {} sheet))
 ([seed sheet]
  (let [relevant-rows (->> (ss/row-seq sheet)
                           (map (fn [row] (map
                                            #(let [x (ss/read-cell %)]
                                               (if (string? x)
                                                  (string/trim x)
                                                  x))
                                             (ss/cell-seq row))))
                           (drop-while
                              (fn drop-header? [cells]
                                  (or (string/blank? (str (first cells))))))
                           (filter #(>= (count %) 3)))
        required-headers (into #{} (map str (keys rekey-2020)))
        [header & value-rows] relevant-rows]
    (when-not (every? (into #{} header) required-headers)
      (binding [*out* *err*]
        (println "Missing some required fields.")
        (println "Required: " (pr-str (keys rekey-2020)))
        (println "Actual:   " (pr-str header))
        (println "Missing:  " (pr-str (remove (into #{} header) (seq required-headers))))))
    (->> value-rows
         (map (fn [cells]
                 (-> (into {} (map vector header cells))
                     (select-keys (keys rekey-2020))
                     rekey
                     (merge seed)
                     map->ScheduleBEntry)))))))

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
  (let [max-length (fn max-length [value-selector] (apply max (map (comp count str value-selector) records)))
        any-nils?  (fn any-nils?  [value-selector] (some (comp nil? value-selector) records))]
    (println "DECLARE @ScheduleB TABLE  (")
    (println "  HCPCSCode       varchar(" (max-length :HCPCSCode)       ") "(if (any-nils? :HCPCSCode)       "" "NOT") " NULL PRIMARY KEY")
    (println " ,ShortDescriptor varchar(" (max-length :ShortDescriptor) ") "(if (any-nils? :ShortDescriptor) "" "NOT") " NULL")
    (println " ,StatusIndicator varchar(" (max-length :StatusIndicator) ") "(if (any-nils? :StatusIndicator) "" "NOT") " NULL")
    (println " ,PaymentRate     REAL NULL,")
    (println " ,Source          varchar(" (max-length :Source)          ") "(if (any-nils? :Source)          "" "NOT") " NULL)")
    (doseq [record records]
      (println
        (str
          "INSERT @ScheduleB VALUES ("
              (sql-literal (.-HCPCSCode record))
          "," (sql-literal (.-ShortDescriptor record))
          "," (sql-literal (.-StatusIndicator record))
          "," (sql-literal (.-PaymentRate record))
          "," (sql-literal (.-Source record))
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

(defn- split-source-arg
  "Splits \"source=path\" into [source path] like
   (split-source-arg \"2020.04=my 2020 scheduleb.xlsx\") => [\"2020.04\" \"my 2020 scheduleb.xlsx\"]
   (split-source-arg \"my 2020 scheduleb.xlsx\") => [\"my 2020 scheduleb.xlsx\" \"my 2020 scheduleb.xlsx\"]"
  [source=xlsx-path]
  (let [parts (string/split source=xlsx-path #"=" 2)
        source   (first parts)
        xlsx-path (last parts)]
    [source xlsx-path]))

(defn load-records
  [source=xlsx-path]
  (let [[source xlsx-path] (split-source-arg  source=xlsx-path)]
    (->> xlsx-path
         ss/load-workbook
         ss/sheet-seq
         first
         (sheet->ScheduleBEntry-seq {:Source source}))))

(defn usage
  []
  (println "HCPCS Schedule B Extractor")
  (println)
  (println "Arguments: <format> <source>=<file.xlsx> [... SourceN=fileN.xlsx]")
  (println "or:        <format>          <file.xlsx> [...         fileN.xlsx]")
  (println "Format: one of CSV, JSON, or SQL.")
  (println "Source: an optional alias representing a useful name for the file.  If not supplied, the filename is used as the source.")
  (println "File:   path to an Excel .xlsx Schedule B workbook from")
  (println "https://www.cms.gov/Medicare/Medicare-Fee-for-Service-Payment/HospitalOutpatientPPS/Addendum-A-and-Addendum-B-Updates"))

(defn -main
  [& [format & source=xlsx-paths]]
 (if (empty? source=xlsx-paths)
  (usage)
  (let [format (string/upper-case (or (and (string/blank? format) "JSON") format))
        printer (or (get printers format)
                    (do (println "Unsupported format:" format) prn))
        records (->> (map load-records source=xlsx-paths)
                     (map (fn [records] (zipmap (map #(.-HCPCSCode %) records) records)))
                     (apply merge {})
                     vals
                     (sort-by #(.-HCPCSCode %)))]
    (printer records)
    (println)

    ; Write count to stderr
    (binding [*out* *err*]
      (println "; loaded/merged" (count records) "HCPS Codes from" (pr-str source=xlsx-paths))))))
