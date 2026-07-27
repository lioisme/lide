param([switch]$Clean)

$ErrorActionPreference = "Stop"
$PROJECT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$GRADLEW = Join-Path $PROJECT_DIR "gradlew.bat"
$WRAPPER_PROPS = Join-Path $PROJECT_DIR "gradle\wrapper\gradle-wrapper.properties"
$VARIANTS = @(
    @{ Name = "x64Debug";   Task = "assembleX64Debug"   },
    @{ Name = "x64Release"; Task = "assembleX64Release" },
    @{ Name = "arm64Debug";   Task = "assembleArm64Debug"   },
    @{ Name = "arm64Release"; Task = "assembleArm64Release" }
)

# --- Swap to local Gradle distribution to bypass SSL issues ---
$originalUrl = $null
$localUrl = $null
if (Test-Path $WRAPPER_PROPS) {
    $content = Get-Content $WRAPPER_PROPS -Raw
    $m = [regex]::Match($content, '(?m)^distributionUrl=(.+)$')
    if ($m.Success) {
        $originalUrl = $m.Groups[1].Value
        $cached = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.3-bin\*\gradle-8.3-bin.zip" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cached) {
            $localUrl = "file\:///$($cached.FullName -replace '\\', '/')"
            $content = $content -replace '(?m)^distributionUrl=.+$', "distributionUrl=$localUrl"
            Set-Content $WRAPPER_PROPS $content
            Write-Host "[*] Using local Gradle: $($cached.FullName)" -ForegroundColor Cyan
        }
    }
}

try {
    if ($Clean) {
        Write-Host "[*] Cleaning..." -ForegroundColor Cyan
        & $GRADLEW clean --no-build-cache 2>&1; if ($LASTEXITCODE) { throw "clean failed" }
    }

    $results = @()
    foreach ($v in $VARIANTS) {
        Write-Host "`n========================================" -ForegroundColor Yellow
        Write-Host "[*] Building $($v.Name)..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Yellow

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & $GRADLEW $v.Task --no-build-cache 2>&1
        $sw.Stop()

        if ($LASTEXITCODE -eq 0) {
            Write-Host "[+] $($v.Name) succeeded in $($sw.Elapsed.TotalSeconds.ToString('F1'))s" -ForegroundColor Green
            $apk = Get-ChildItem (Join-Path $PROJECT_DIR "app\build\outputs\apk") -Recurse -Filter "*.apk" | Where-Object { $_.Name -like "*$($v.Name)*" } | Select-Object -First 1
            $size = if ($apk) { "{0:N0} KB" -f ($apk.Length/1KB) } else { "N/A" }
            $results += [PSCustomObject]@{ Variant = $v.Name; Status = "OK"; Size = $size }
        } else {
            Write-Host "[-] $($v.Name) FAILED" -ForegroundColor Red
            $results += [PSCustomObject]@{ Variant = $v.Name; Status = "FAILED"; Size = "" }
        }
    }

    Write-Host "`n========================================" -ForegroundColor Yellow
    Write-Host "[*] Summary" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Yellow
    $results | Format-Table -AutoSize
} finally {
    # Restore original distribution URL
    if ($originalUrl -and (Test-Path $WRAPPER_PROPS)) {
        $content = Get-Content $WRAPPER_PROPS -Raw
        $content = $content -replace '(?m)^distributionUrl=.+$', "distributionUrl=$originalUrl"
        Set-Content $WRAPPER_PROPS $content
        Write-Host "[*] Restored original Gradle distribution URL" -ForegroundColor Cyan
    }
}

$failed = $results | Where-Object { $_.Status -eq "FAILED" }
if ($failed) { Write-Host "[-] $($failed.Count) build(s) failed" -ForegroundColor Red; exit 1 }
else { Write-Host "[+] All 4 APKs built successfully" -ForegroundColor Green }
