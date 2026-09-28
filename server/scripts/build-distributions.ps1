$ErrorActionPreference = "Stop"

$projectDirectory = Split-Path -Parent $PSScriptRoot
$distributionDirectory = Join-Path $projectDirectory "dist"

if (Test-Path $distributionDirectory) {
    Remove-Item $distributionDirectory -Recurse -Force
}

New-Item $distributionDirectory -ItemType Directory | Out-Null

function Build-Distribution {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("sqlite", "postgres")]
        [string] $Profile
    )

    Write-Host ""
    Write-Host "Building $Profile distribution..."

    Push-Location $projectDirectory

    try {
        & ".\mvnw.cmd" clean package "-P$Profile" "-DskipTests"

        if ($LASTEXITCODE -ne 0) {
            throw "The $Profile build failed."
        }

        $source = Join-Path $projectDirectory "target\quarkus-app"
        $destination = Join-Path $distributionDirectory $Profile

        Copy-Item `
            -Path $source `
            -Destination $destination `
            -Recurse
    }
    finally {
        Pop-Location
    }
}

Build-Distribution -Profile "sqlite"
Build-Distribution -Profile "postgres"

Write-Host ""
Write-Host "Distributions created:"
Write-Host "  dist\sqlite"
Write-Host "  dist\postgres"