param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$Gradle = "D:\Gradle\gradle-9.5.1\bin\gradle.bat"

$WorkDir = $PSScriptRoot

$variants = @(
    "x64Debug",
    "x64Release",
    "arm64Debug",
    "arm64Release"
)

if ($Clean) {
    Write-Host "=== Cleaning build outputs ===" -ForegroundColor Yellow
    Push-Location $WorkDir
    & cmd /c "$Gradle clean"
    Pop-Location
    Write-Host ""
    return
}

Write-Host "=== Building 4 APKs ===" -ForegroundColor Cyan
Write-Host ""

Push-Location $WorkDir

foreach ($variant in $variants) {
    $task = ":app:assemble${variant}"
    Write-Host ">>> Building ${variant}..." -ForegroundColor Green
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    & cmd /c "$Gradle $task"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "!!! FAILED: ${variant}" -ForegroundColor Red
        Pop-Location
        exit 1
    }

    $sw.Stop()
    $seconds = [math]::Round($sw.Elapsed.TotalSeconds, 1)
    Write-Host ">>> ${variant} done (${seconds}s)" -ForegroundColor Green
    Write-Host ""
}

Pop-Location

Write-Host "=== All 4 APKs built successfully ===" -ForegroundColor Cyan
Write-Host ""

$apkDir = "$WorkDir\app\build\outputs\apk"
foreach ($variant in $variants) {
    $arch = if ($variant -like "x64*") { "x64" } else { "arm64" }
    $type = if ($variant -like "*Debug") { "debug" } else { "release" }
    $apk = "$apkDir\$arch\$type\app-$variant.apk"
    if (Test-Path $apk) {
        $sizeKB = [math]::Round((Get-Item $apk).Length / 1KB, 0)
        Write-Host "  $apk  ($sizeKB KB)"
    }
}