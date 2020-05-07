(defproject com.softekpanther.cms "0.1.0-SNAPSHOT"
  :description "Read HCPCS codes from Excel sheet"
  :main com.softekpanther.cms.hcpcs
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [dk.ative/docjure "1.13.0"]]

  :profiles {:uberjar {:aot :all}})
