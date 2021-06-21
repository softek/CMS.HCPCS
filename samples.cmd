@echo off
pushd %~dp0
ECHO Building uberjar
call build.cmd
call :buildSample CSV         sample.csv
call :buildSample JSON        sample.json
call :buildSample SQL         sample.sql
popd
goto :EOF

:buildSample
ECHO ==========================================================================
ECHO Building %1 -> %2
java -jar target\com.softekpanther.cms-0.1.1-SNAPSHOT-standalone.jar %1 status-indicators.csv ^
2019="january_2019_opps_web_addendum_b.12312018.xlsx" ^
2020="2020_january_web_addendum_b.12312019.xlsx" ^
2020-04="January 2020 Addendum B CORRECTION.02042020.xlsx" ^
2020-05-04="2020_april_web_addendum_b.05042020.xlsx" ^
2021-01="2021_January_Web_Addendum_B.12.29.20.xlsx" ^
2021-06="April_2021_OPPS_Web_Addendum_B.03222021.xlsx" > %2
