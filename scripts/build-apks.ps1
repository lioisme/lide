param(
    [switch]$Clean,
    # ABI 组合矩阵（默认 = 官方 6 变体：x64/arm64/universal × Debug/Release）。
    # 支持任意 ABI 组合（含未来新架构）：-Abis "arm64-v8a,x86_64" -Types "Debug,Release"
    # 或直接用 -Variants 指定完整变体名列表（覆盖 -Abis/-Types）。
    [string[]]$Abis = @("x64", "arm64", "universal"),
    [string[]]$Types = @("Debug", "Release"),
    [string[]]$Variants = @()
)

# Build APKs (default 6: x64/arm64/universal x Debug/Release) as independent variant tasks.
# universal = x86_64 + arm64-v8a fat build（打包器 ABI 自由组合的宿主载体）。
# Signed with project-local keystore.
# Env-neutral: writes only inside the project; uses project gradlew;
# SDK located via local.properties / ANDROID_HOME (read-only).

$ErrorActionPreference = "Stop"

$Root   = Split-Path $PSScriptRoot -Parent
$App    = Join-Path $Root "app"
$Gradle = Join-Path $Root "gradlew.bat"
$ksProps = Join-Path $App "keystore.properties"
$ksFile  = Join-Path $App "keystore.jks"

# 变体矩阵：优先 -Variants 显式列表；否则 Abis × Types 笛卡尔积。
if ($Variants.Count -gt 0) {
    $matrix = @($Variants)
} else {
    $matrix = @()
    foreach ($abi in $Abis) {
        foreach ($type in $Types) {
            $matrix += "$abi$type"
        }
    }
}

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
            if ($line -like "sdk.dir=*") {
                # local.properties 是 java.util.Properties 转义语法（\: 与 \\），需还原成 Windows 路径
                $dir = $line.Substring("sdk.dir=".Length).Trim().Replace("\:", ":").Replace("\\", "\")
                if (Test-Path -LiteralPath $dir) { return $dir }
            }
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

Write-Host "=== Building $($matrix.Count) APKs: $($matrix -join ', ') ===" -ForegroundColor Cyan
Push-Location $Root
try {
    foreach ($variant in $matrix) {
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
foreach ($variant in $matrix) {
    # 变体 → 目录：camel 边界拆分（x64Debug → x64/debug）；universal 与 arm 前缀需按已知 flavor 匹配。
    $arch = ""; $type = ""
    foreach ($known in @("x64", "arm64", "universal")) {
        if ($variant -like "$known*") { $arch = $known; break }
    }
    if ($arch -eq "") { throw "Unknown flavor in variant: $variant" }
    if ($variant -like "*Debug") { $type = "debug" } elseif ($variant -like "*Release") { $type = "release" } else { throw "Unknown build type in variant: $variant" }
    $apk = Join-Path $apkDir "$arch\$type\app-$arch-$type.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
    $sizeKB = [math]::Round((Get-Item -LiteralPath $apk).Length / 1KB, 0)
    Write-Host "  $apk  ($sizeKB KB)"
    Verify-ApkSigning $apk $apksigner
}
Write-Host ""
Write-Host "=== All $($matrix.Count) APKs built and signed ===" -ForegroundColor Cyan
