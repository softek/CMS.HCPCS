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
2023-01="2023_January_Web_Addendum_B.12212022.xlsx" > %2
