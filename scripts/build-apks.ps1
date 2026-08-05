param(
    [switch]$Clean
)

# Build 4 APKs (x64/arm64 x Debug/Release) as independent variant tasks.
# No merged/universal build. Signed with project-local keystore.
# Env-neutral: writes only inside $PSScriptRoot; uses project gradlew;
# SDK located via local.properties / ANDROID_HOME (read-only).

$ErrorActionPreference = "Stop"

$Root   = $PSScriptRoot
$App    = Join-Path $Root "app"
$Gradle = Join-Path $Root "gradlew.bat"
$ksProps = Join-Path $App "keystore.properties"
$ksFile  = Join-Path $App "keystore.jks"
$variants = @("x64Debug", "x64Release", "arm64Debug", "arm64Release")

function Read-KsProps {
    param($path)
    $map = @{}
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path) {
            $i = $line.IndexOf("=")
            if ($i -gt 0) { $map[$line.Substring(0,$i).Trim()] = $line.Substring($i+1).Trim() }
        }
    }
    return $map
}


function Ensure-Keystore {
    if (Test-Path -LiteralPath $ksFile) { return }
    if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
        throw "keytool not found in PATH; cannot bootstrap project keystore"
    }
    Write-Host "Generating project-local signing keystore: $ksFile" -ForegroundColor Yellow
    & keytool -genkeypair -v -keystore $ksFile -storetype PKCS12 -alias lide -keyalg RSA -keysize 2048 -validity 10000 -storepass lide123 -keypass lide123 -dname "CN=Lide,OU=Lide,O=Lide,L=Shenzhen,ST=GD,C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed to create keystore" }
    @("storeFile=keystore.jks","storePassword=lide123","keyAlias=lide","keyPassword=lide123") | Set-Content -LiteralPath $ksProps -Encoding ASCII
}

function Resolve-SdkDir {
    $loc = Join-Path $Root "local.properties"
    if (Test-Path -LiteralPath $loc) {
        foreach ($line in Get-Content -LiteralPath $loc) {
            if ($line -like "sdk.dir=*") { return $line.Substring("sdk.dir=".Length).Trim() }
        }
    }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    return $null
}

function Resolve-ApkSigner {
    param($sdk)
    if (-not $sdk) { return $null }
    $bt = Join-Path $sdk "build-tools"
    if (-not (Test-Path -LiteralPath $bt)) { return $null }
    $latest = Get-ChildItem -LiteralPath $bt -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    $tool = Join-Path $latest.FullName "apksigner.bat"
    if (Test-Path -LiteralPath $tool) { return $tool }
    return $null
}

function Verify-ApkSigning {
    param($apk, $apksigner)
    if (-not $apksigner) {
        Write-Host "  [warn] apksigner not found; skip signature verification" -ForegroundColor Yellow
        return
    }
    & cmd /c "`"$apksigner`" verify --verbose --print-certs `"$apk`""
    if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $apk" }
}

if ($Clean) {
    Write-Host "=== Cleaning build outputs ===" -ForegroundColor Yellow
    Push-Location $Root
    try { & cmd /c "`"$Gradle`" clean"; if ($LASTEXITCODE -ne 0) { throw "gradle clean failed" } }
    finally { Pop-Location }
    return
}

Ensure-Keystore

Write-Host "=== Building 4 APKs (independent variants, no merged build) ===" -ForegroundColor Cyan
Push-Location $Root
try {
    foreach ($variant in $variants) {
        $task = ":app:assemble${variant}"
        Write-Host ">>> $task" -ForegroundColor Green
        & cmd /c "`"$Gradle`" $task --console=plain"
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $task" }
    }
}
finally { Pop-Location }

Write-Host ""
Write-Host "=== Signing verification ===" -ForegroundColor Cyan
$sdk = Resolve-SdkDir
$apksigner = Resolve-ApkSigner $sdk

$apkDir = Join-Path $App "build\outputs\apk"
foreach ($variant in $variants) {
    $arch = ""; $type = ""
    if ($variant -like "x64*") { $arch = "x64" } else { $arch = "arm64" }
    if ($variant -like "*Debug") { $type = "debug" } else { $type = "release" }
    $apk = Join-Path $apkDir "$arch\$type\app-$arch-$type.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
    $sizeKB = [math]::Round((Get-Item -LiteralPath $apk).Length / 1KB, 0)
    Write-Host "  $apk  ($sizeKB KB)"
    Verify-ApkSigning $apk $apksigner
}
Write-Host ""
Write-Host "=== All 4 APKs built and signed ===" -ForegroundColor Cyan
