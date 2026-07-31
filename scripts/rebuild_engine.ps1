# delua AndroLua Engine Rebuild Script (Windows)
# Usage: .\scripts\rebuild_engine.ps1 [-ABI <arm64-v8a|armeabi-v7a|x86_64|all>]
# Requires: ANDROID_NDK_HOME

param(
    [ValidateSet("arm64-v8a", "armeabi-v7a", "x86_64", "all")]
    [string]$ABI = "all"
)

$ErrorActionPreference = "Stop"

# Resolve delua project root robustly (automatic variables can be empty
# depending on how the script is invoked)
$DELUA_ROOT = $null
$scriptCandidate = $null
if ($PSScriptRoot) { $scriptCandidate = Split-Path -Parent $PSScriptRoot }
if (-not $scriptCandidate -and $MyInvocation.MyCommand.Path) {
    $scriptCandidate = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
}
if (-not $scriptCandidate) { $scriptCandidate = (Get-Location).Path }

foreach ($c in @($scriptCandidate, (Get-Location).Path)) {
    if ($c -and (Test-Path (Join-Path $c "settings.gradle.kts"))) {
        $DELUA_ROOT = $c
        break
    }
    if ($c -and (Test-Path (Join-Path $c "scripts\rebuild_engine.ps1"))) {
        $DELUA_ROOT = $c
        break
    }
}
if (-not $DELUA_ROOT) {
    Write-Error "Cannot locate delua project root. Run this script from the project root or scripts/ directory."
    exit 1
}
$PREBUILT_DIR = Join-Path $DELUA_ROOT "prebuilt"
$JNILIBS_DIR = Join-Path $DELUA_ROOT "app\src\main\jniLibs"
$JNI_SRC_DIR = Join-Path $DELUA_ROOT "docs\AndroLua_pro-master\app\src\main\jni"
$JNI_CFG_DIR = Join-Path $DELUA_ROOT "build-scripts\jni"
$BUILD_DIR = Join-Path $env:TEMP "delua-ndk-build"
$BUILD_LOG = Join-Path $BUILD_DIR "build.log"

# Disabled modules kept in reference tree (Android.mk2 only / no build file)
$DISABLED_MODULES = @("lluv", "lsqlite3", "tcc", "lfs")

# Expected shared library modules (excluding luajava which is the base dependency)
$EXPECTED_MODULES = @(
    "cjson", "md5", "crypt", "zip", "xml", "regex",
    "zlib", "canvas", "bson", "luv", "socket", "mime",
    "sensor", "gl"
)

# 1. Locate NDK
$NDK_DIR = $env:ANDROID_NDK_HOME
if (-not $NDK_DIR) {
    $candidates = @(
        "D:\Android\ndk\29.0.14206865",
        "D:\Android\ndk\28.2.13676358",
        "D:\Android\ndk\27.0.12077973",
        "D:\Android\ndk\25.1.8937393",
        "$env:LOCALAPPDATA\Android\Sdk\ndk\*"
    )
    foreach ($c in $candidates) {
        $resolved = Resolve-Path $c -ErrorAction SilentlyContinue
        if ($resolved) { $NDK_DIR = $resolved[-1].Path; break }
    }
}
if (-not $NDK_DIR -or -not (Test-Path (Join-Path $NDK_DIR "ndk-build.cmd"))) {
    Write-Error "NDK not found. Set ANDROID_NDK_HOME or install NDK."
    exit 1
}
Write-Host "[1/5] NDK: $NDK_DIR"

# 2. Clean and prepare build directory
Write-Host "[2/5] Preparing build directory..."
if (Test-Path $BUILD_DIR) { Remove-Item $BUILD_DIR -Recurse -Force }
New-Item -ItemType Directory $BUILD_DIR -Force | Out-Null

# Copy reference JNI source tree (docs/AndroLua_pro-master), then overlay
# delua-managed Android.mk build configs from build-scripts/jni/
$JNI_DIR = Join-Path $BUILD_DIR "jni"
Copy-Item $JNI_SRC_DIR $JNI_DIR -Recurse -Force

Get-ChildItem $JNI_CFG_DIR -Filter "Android.mk" -Recurse | ForEach-Object {
    $relPath = $_.FullName.Substring($JNI_CFG_DIR.Length + 1)
    Copy-Item $_.FullName (Join-Path $JNI_DIR $relPath) -Force
}

# Remove disabled module directories
foreach ($d in $DISABLED_MODULES) {
    $path = Join-Path $JNI_DIR $d
    if (Test-Path $path) { Remove-Item $path -Recurse -Force }
}

# Apply source patches from build-scripts/patches/ (files overlay the
# reference tree; keeps docs/ reference pristine)
$PATCH_DIR = Join-Path $DELUA_ROOT "build-scripts\patches"
if (Test-Path $PATCH_DIR) {
    Get-ChildItem $PATCH_DIR -Recurse -File | ForEach-Object {
        $relPath = $_.FullName.Substring($PATCH_DIR.Length + 1)
        $target = Join-Path $JNI_DIR $relPath
        New-Item -ItemType Directory (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item $_.FullName $target -Force
        Write-Host "  patched: $relPath"
    }
}

# Remove standalone executables
Remove-Item (Join-Path $JNI_DIR "lua\lua.c") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $JNI_DIR "lua\luac.c") -Force -ErrorAction SilentlyContinue

# 3. Write Application.mk with modern settings
Write-Host "[3/5] Writing Application.mk..."
$abiList = if ($ABI -eq "all") { "arm64-v8a armeabi-v7a x86_64" } else { $ABI }
@"
APP_ABI := $abiList
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release
"@ | Set-Content (Join-Path $JNI_DIR "Application.mk") -Encoding ASCII

# List modules that will be built
Write-Host "`nModules to build:"
Get-ChildItem $JNI_DIR -Filter "Android.mk" -Recurse | ForEach-Object {
    $mod = $_.Directory.Name
    $line = Select-String -Path $_.FullName -Pattern "LOCAL_MODULE\s+:=" | Select-Object -First 1
    $lib = if ($line) { $line.ToString().Split(":=")[-1].Trim() } else { "?" }
    Write-Host "  - $mod -> $lib"
}

# 4. Run NDK build with output capture
$NDK_BUILD = Join-Path $NDK_DIR "ndk-build.cmd"
Write-Host "`n[4/5] Running ndk-build (ABI=$ABI)..."
$env:NDK_PROJECT_PATH = $BUILD_DIR

# Capture both stdout and stderr to log file via cmd.exe for reliable
# exit code and full fidelity of native tool output
cmd.exe /c "`"$NDK_BUILD`" -j4 > `"$BUILD_LOG`" 2>&1"
$buildExitCode = $LASTEXITCODE

if ($buildExitCode -ne 0) {
    Write-Host "`n=== BUILD FAILED (exit code $buildExitCode) ===" -ForegroundColor Red
    Write-Host "Last 50 lines of build log:"
    Get-Content $BUILD_LOG -Tail 50
    Write-Host "`nFull log: $BUILD_LOG"
    exit $buildExitCode
}

# ACC-003: verify no hard error patterns remain in a successful build
$hardErrors = Select-String -Path $BUILD_LOG -Pattern "error:|fatal" 
if ($hardErrors) {
    Write-Host "`nWARNING: build exit code is 0 but log contains error patterns:" -ForegroundColor Yellow
    $hardErrors | Select-Object -First 20 | ForEach-Object { Write-Host "  $($_.Line)" }
}

# 5. Copy artifacts to prebuilt/ and jniLibs/
Write-Host "`n[5/5] Copying artifacts..."
$ABI_LIST = if ($ABI -eq "all") { @("arm64-v8a", "armeabi-v7a", "x86_64") } else { @($ABI) }
$totalModules = 0
$successModules = 0
$failedModules = @()

foreach ($abi in $ABI_LIST) {
    $libsDir = Join-Path (Join-Path $BUILD_DIR "libs") $abi
    if (-not (Test-Path $libsDir)) {
        Write-Host "  WARNING: No libraries built for $abi"
        continue
    }

    # Build list of produced .so files for this ABI
    $produced = Get-ChildItem $libsDir -Filter "*.so" | ForEach-Object { $_.BaseName -replace "^lib", "" }

    # Copy to prebuilt/
    $prebuiltAbi = Join-Path $PREBUILT_DIR $abi
    New-Item -ItemType Directory $prebuiltAbi -Force | Out-Null
    Get-ChildItem $libsDir -Filter "*.so" | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $prebuiltAbi $_.Name) -Force
        Write-Host "  prebuilt/$abi/$($_.Name)"
    }

    # Copy to jniLibs/
    $jniAbi = Join-Path $JNILIBS_DIR $abi
    New-Item -ItemType Directory $jniAbi -Force | Out-Null
    Get-ChildItem $libsDir -Filter "*.so" | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $jniAbi $_.Name) -Force
    }

    # Validate expected modules
    foreach ($mod in $EXPECTED_MODULES) {
        $totalModules++
        if ($mod -in $produced) {
            $successModules++
        } else {
            $failedModules += "$abi/$mod"
        }
    }
}

# Build summary
Write-Host "`n=== Build Summary ==="
Get-ChildItem $PREBUILT_DIR -Recurse -Filter "*.so" | ForEach-Object {
    $size = [math]::Round($_.Length / 1KB, 1)
    Write-Host "  $($_.Directory.Name)/$($_.Name)  ${size}KB"
}

$totalExpected = $EXPECTED_MODULES.Count * $ABI_LIST.Count
Write-Host "`nBuild summary: $successModules/$totalExpected modules succeeded"
if ($failedModules.Count -gt 0) {
    Write-Host "Missing modules:" -ForegroundColor Yellow
    $failedModules | ForEach-Object { Write-Host "  FAILED: $_" -ForegroundColor Red }
    Write-Host "`nDetailed build log: $BUILD_LOG"
    exit 1
}

Write-Host "`n✅ Engine rebuild complete"
