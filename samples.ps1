Function Convert-Addendum
{
    Param(
        [Parameter(mandatory=$true)][string] $Type,
        [Parameter(mandatory=$true)][string] $OutFile
    )

    Write-Host "=========================================================================="
    Write-Host "Building $Type -> $OutFile"

    $date = Get-Date -Format "yyyy-MM"
    $addendum = Get-ChildItem "*addendum*b*.xlsx"

    java -jar target\com.softekpanther.cms-0.1.1-SNAPSHOT-standalone.jar $Type status-indicators.csv "$Date=$addendum" |
    Out-File -Path $OutFile -Encoding oem
}

Write-Host "Building uberjar"
.\build.cmd
Convert-Addendum "CSV" "sample.csv"
Convert-Addendum "JSON" "sample.json"
Convert-Addendum "SQL" "sample.sql"

# replace empty strings with nulls in JSON, see panther-17125 for more info.
(Get-Content sample.json) -replace '"PaymentRate":\s*""', '"PaymentRate":null' | Set-Content sample.json
