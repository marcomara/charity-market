param(
    [Parameter()]
    [string] $DatabaseHost = "localhost",

    [Parameter()]
    [int] $DatabasePort = 5432,

    [Parameter()]
    [string] $DatabaseName = "charitymarket",

    [Parameter()]
    [string] $DatabaseUsername = "charitymarket",

    [Parameter(Mandatory = $true)]
    [string] $DatabasePassword,

    [Parameter()]
    [int] $ServerPort = 8080
)

$ErrorActionPreference = "Stop"

$jarPath = Join-Path `
    $PSScriptRoot `
    "target\quarkus-app\quarkus-run.jar"

if (-not (Test-Path $jarPath)) {
    throw @"
The packaged server was not found.

Build it first with:
.\mvnw.cmd clean package "-Ppostgres" "-DskipTests"
"@
}

$javaArguments = @(
    "-Dquarkus.profile=postgres",
    "-Dquarkus.http.port=$ServerPort",
    "-Dcharity.db.host=$DatabaseHost",
    "-Dcharity.db.port=$DatabasePort",
    "-Dcharity.db.name=$DatabaseName",
    "-Dcharity.db.username=$DatabaseUsername",
    "-Dcharity.db.password=$DatabasePassword",
    "-jar",
    $jarPath
)

Write-Host "Starting Charity Market server"
Write-Host "Database host: $DatabaseHost"
Write-Host "Database port: $DatabasePort"
Write-Host "Database name: $DatabaseName"
Write-Host "Database user: $DatabaseUsername"
Write-Host "Server port: $ServerPort"

& java @javaArguments