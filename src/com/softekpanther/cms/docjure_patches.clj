; Monkey-patch
; with code borrowed from https://github.com/mjul/docjure/pull/16/files
; and updated for Docjure 14
; until the functionality is part of the library.
(ns com.softekpanther.cms.docjure-patches
  (:require dk.ative.docjure.spreadsheet)
  (:import
   (org.apache.poi.hssf.usermodel HSSFFormulaEvaluator)
   (org.apache.poi.ss.usermodel Cell CellType CellValue DateUtil)))

(def ^:dynamic *ignore-missing-workbooks*
  true)

(def ^:dynamic *ignore-formulas*
  false)

(defmethod dk.ative.docjure.spreadsheet/read-cell-value CellType/ERROR  [^CellValue cv _]
  (str "Error cell: " cv))

(defmethod dk.ative.docjure.spreadsheet/read-cell CellType/FORMULA   [^Cell cell]
  (if *ignore-formulas*
    (condp = (.getCachedFormulaResultType cell)
      CellType/BLANK   nil
      CellType/STRING  (.getStringCellValue cell)
      CellType/BOOLEAN (.getBooleanCellValue cell)
      CellType/NUMERIC (if (DateUtil/isCellDateFormatted cell)
                         (.getDateCellValue cell)
                         (.getNumericCellValue cell))
      (throw (ex-info "Unable to read cell value when ignoring formulas"
                      {:cell cell
                       :cached-formula-result-type (.getCachedFormulaResultType cell)})))
    (let [evaluator (.. cell getSheet getWorkbook
                        getCreationHelper createFormulaEvaluator)]
      (when (instance? HSSFFormulaEvaluator evaluator)
        (.setIgnoreMissingWorkbooks evaluator *ignore-missing-workbooks*))
      (dk.ative.docjure.spreadsheet/read-cell-value (.evaluate evaluator cell) false))))

(defmethod dk.ative.docjure.spreadsheet/read-cell CellType/ERROR [^Cell cell]
  "ERROR")
