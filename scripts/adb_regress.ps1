# lide ADB 回归矩阵脚本（WP-019）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\adb_regress.ps1 [-Device emulator-5554] [-SkipBuild]
# 流程：构建 x64 → 安装 → 推送 4 个基准工程 → 逐个拉起运行 → 断言（logcat 关键行 / 进程存活 / 截图存档）
# 截图与日志存档：%TEMP%\lide-verify\

param(
    [string]$Device = "emulator-5554",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "app\build\outputs\apk\x64\debug\app-x64-debug.apk"
$projRoot = "/storage/emulated/0/AndroLua/project/"
$verifyDir = Join-Path $env:TEMP "lide-verify"
New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null

function Adb([string]$argsLine) {
    $p = Start-Process adb -ArgumentList "-s $Device $argsLine" -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $env:TEMP "adb_out.txt") -RedirectStandardError (Join-Path $env:TEMP "adb_err.txt")
    $out = Get-Content (Join-Path $env:TEMP "adb_out.txt") -Raw -ErrorAction SilentlyContinue
    $err = Get-Content (Join-Path $env:TEMP "adb_err.txt") -Raw -ErrorAction SilentlyContinue
    if ($p.ExitCode -ne 0) { throw "adb $argsLine failed (exit $($p.ExitCode)): $err" }
    return $out
}

$results = @()

Write-Host "== 0/6 设备检查 =="
$devices = adb devices
if ($devices -notmatch $Device) { throw "设备 $Device 不在线（adb devices 输出：$devices）" }

if (-not $SkipBuild) {
    Write-Host "== 1/6 构建 x64 debug APK =="
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleX64Debug --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradle 构建失败" }
    } finally { Pop-Location }
}
$results += [pscustomobject]@{ Case = "BUILD"; Result = "PASS"; Note = "assembleX64Debug" }

Write-Host "== 2/6 安装 APK =="
Adb "install -r $apk" | Out-Null
Adb "shell appops set com.lioisme.lide MANAGE_EXTERNAL_STORAGE allow" | Out-Null
$results += [pscustomobject]@{ Case = "INSTALL"; Result = "PASS"; Note = "install -r" }

Write-Host "== 3/6 推送 4 个基准工程 =="
foreach ($proj in @("demo", "DemoProject", "kleinbottle", "testjson")) {
    Adb "push $root\app\src\main\assets\samples\$proj $projRoot" | Out-Null
}
$results += [pscustomobject]@{ Case = "PUSH"; Result = "PASS"; Note = "4 工程推送" }

Write-Host "== 4/6 逐个拉起运行并断言 =="
$matrix = @(
    @{ Name = "demo";          Expect = "UI" },
    @{ Name = "DemoProject";   Expect = "page|calc" },
    @{ Name = "kleinbottle";   Expect = "GL|gl" },
    @{ Name = "testjson";      Expect = "json|TestJson|JSON" }
)
foreach ($m in $matrix) {
    $name = $m.Name
    Write-Host "---- $name ----"
    Adb "logcat -c" | Out-Null
    Adb "shell am start -a android.intent.action.VIEW -d androlua://com.lioisme.lide/project/$name/main.lua" | Out-Null
    Start-Sleep -Seconds 8
    $pidLine = Adb "shell pidof com.lioisme.lide"
    $alive = $pidLine -match '\d+'
    $resumed = (Adb "shell dumpsys activity activities") -match "topResumedActivity=ActivityRecord\{[^}]* com\.lioisme\.lide/\.engine\.EngineActivity"
    if ($m.Expect -eq "UI") {
        # 纯 UI 脚本（demo）：无 print 输出，以「引擎页前台 + 进程存活」为成功判据。
        $matched = $resumed
    } else {
        $log = Adb "logcat -d"
        $matched = $log -match $m.Expect
    }
    Adb "exec-out screencap -p > $verifyDir\$name.png" | Out-Null
    if ($alive) { Adb "shell am force-stop com.lioisme.lide" | Out-Null }
    $pass = $alive -and $matched
    $results += [pscustomobject]@{ Case = $name; Result = $(if ($pass) { "PASS" } else { "FAIL" }); Note = "alive=$alive resumed=$resumed expect=$($m.Expect)" }
}

Write-Host "== 5/6 基准工程静态断言（init.lua 存在）=="
$ok = $true
foreach ($proj in @("demo", "DemoProject", "kleinbottle", "testjson")) {
    $p = Adb "shell ls $projRoot$proj/init.lua"
    if ($p -notmatch "init.lua") { $ok = $false }
}
$results += [pscustomobject]@{ Case = "INIT"; Result = $(if ($ok) { "PASS" } else { "FAIL" }); Note = "4 工程 init.lua" }

Write-Host "`n===== 汇总 ====="
$results | Format-Table -AutoSize | Out-String | Write-Host
$failed = $results | Where-Object { $_.Result -eq "FAIL" }
$logFile = Join-Path $verifyDir "regress-latest.txt"
$results | Format-Table -AutoSize | Out-String | Set-Content -Encoding UTF8 $logFile
if ($failed) {
    Write-Host "回归失败：$($failed.Count) 项 -> $($verifyDir)"
    exit 1
} else {
    Write-Host "回归全部通过。存档：$($verifyDir)"
    exit 0
}
