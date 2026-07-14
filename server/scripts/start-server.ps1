param(
    [Parameter()]
    [ValidateSet("sqlite", "postgres")]
    [string] $DatabaseMode = "sqlite",

    [Parameter()]
    [int] $Port = 8080,

    [Parameter()]
    [string] $DatabaseUrl,

    [Parameter()]
    [string] $DatabaseUser,

    [Parameter()]
    [string] $DatabasePassword
)

$ErrorActionPreference = "Stop"

$projectDirectory = Split-Path -Parent $PSScriptRoot
$distribution = Join-Path $projectDirectory "dist\$DatabaseMode"
$jar = Join-Path $distribution "quarkus-run.jar"

if (-not (Test-Path $jar)) {
    throw "Distribution not found: $jar. Run build-distributions.ps1 first."
}

$javaArguments = @(
    "-Dquarkus.http.port=$Port"
)

switch ($DatabaseMode) {
    "sqlite" {
        if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
            $dataDirectory = Join-Path $projectDirectory "data"

            New-Item `
                -Path $dataDirectory `
                -ItemType Directory `
                -Force | Out-Null

            $databaseFile = Join-Path `
                $dataDirectory `
                "charity-market.db"

            $DatabaseUrl = "jdbc:sqlite:$databaseFile"
        }

        $javaArguments +=
        "-Dquarkus.datasource.jdbc.url=$DatabaseUrl"
    }

    "postgres" {
        if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
            throw "DatabaseUrl is required for PostgreSQL."
        }

        if ([string]::IsNullOrWhiteSpace($DatabaseUser)) {
            throw "DatabaseUser is required for PostgreSQL."
        }

        if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
            throw "DatabasePassword is required for PostgreSQL."
        }

        $javaArguments += @(
            "-Dquarkus.datasource.jdbc.url=$DatabaseUrl",
            "-Dquarkus.datasource.username=$DatabaseUser",
            "-Dquarkus.datasource.password=$DatabasePassword"
        )
    }
}

$javaArguments += @(
    "-jar",
    $jar
)

Write-Host "Starting Charity Market server"
Write-Host "Database mode: $DatabaseMode"
Write-Host "Port: $Port"

& java @javaArguments