$ErrorActionPreference = "Stop"

$serverDirectory = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $serverDirectory

$desktopServerDirectory = Join-Path `
    $projectRoot `
    "desktopApp\app-resources\common\local-server"

Push-Location $serverDirectory

try {
    & ".\mvnw.cmd" `
        clean package `
        "-Psqlite" `
        "-DskipTests"

    if ($LASTEXITCODE -ne 0) {
        throw "The SQLite server build failed."
    }
}
finally {
    Pop-Location
}

if (Test-Path $desktopServerDirectory) {
    Remove-Item `
        $desktopServerDirectory `
        -Recurse `
        -Force
}

New-Item `
    -ItemType Directory `
    -Path $desktopServerDirectory `
    -Force | Out-Null

Copy-Item `
    -Path (
Join-Path `
            $serverDirectory `
            "target\quarkus-app\*"
) `
    -Destination $desktopServerDirectory `
    -Recurse `
    -Force

Write-Host "Current SQLite server copied to:"
Write-Host $desktopServerDirectory