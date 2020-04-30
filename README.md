# com.softekpanther.cms HCPCS Schedule B converter

A Clojure library designed to read the HCPCS codes from the CMS.gov Schedule B Excel workbooks and convert

## Usage

    lein run CSV  "January 2020 Addendum B CORRECTION.02042020.xlsx"
    lein run JSON "January 2020 Addendum B CORRECTION.02042020.xlsx"
    lein run SQL  "January 2020 Addendum B CORRECTION.02042020.xlsx"

It also works merges updates.  Just put the later ones later in the argument list, like so:

    lein run SQL  january_2019_opps_web_addendum_b.12312018.xlsx "January 2020 Addendum B CORRECTION.02042020.xlsx"

Or, to save the JSON.

    lein run JSON "January 2020 Addendum B CORRECTION.02042020.xlsx" > 02042020.json

Examples:

    C:\ws\softek\hcpcs>lein run JSON "January 2020 Addendum B CORRECTION.02042020.xlsx" > "January 2020 Addendum B CORRECTION.02042020.xlsx".json
    ; 1683 HCPS Codes:
    C:\ws\softek\hcpcs>lein run CSV "January 2020 Addendum B CORRECTION.02042020.xlsx" > "January 2020 Addendum B CORRECTION.02042020.xlsx".csv
    ; 1683 HCPS Codes:

* [csv](January%202020%20Addendum%20B%20CORRECTION.02042020.xlsx.csv)
* [json](January%202020%20Addendum%20B%20CORRECTION.02042020.xlsx.json)
* [sql – T-SQL table variable](January%202020%20Addendum%20B%20CORRECTION.02042020.xlsx.sql)


## Source data

Source data may be found by downloading the latest "Schedule B" from https://www.cms.gov/Medicare/Medicare-Fee-for-Service-Payment/HospitalOutpatientPPS/Addendum-A-and-Addendum-B-Updates

