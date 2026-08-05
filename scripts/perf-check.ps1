#Requires -Version 5.1
<#
.SYNOPSIS
  lide 性能基准脚本（ADB 设备侧）。任务单 F8 P2 §3。
.DESCRIPTION
  四类基准，输出表格 + 建议阈值判定：
    cold  - 冷启动耗时（am start -W -S，3 次取中位数）
    gfx   - 编辑器滚动 jank（gfxinfo Total/Janky frames，jank 判定 <=2）
    open  - 77KB Lua 文件打开到编辑器（androlua:// 深链 am start -W WaitTime）
    type  - 编辑器连续输入 200 字符后的 jank（gfxinfo）
  阈值仅为建议值（模拟器/真机差异大），PASS/FAIL 以实测同机 A/B 为准。
.EXAMPLE
  .\scripts\perf-check.ps1 -Serial emulator-5554 -Scenario all
  .\scripts\perf-check.ps1 -Scenario cold
#>
param(
    [string]$Serial = $env:ANDROID_SERIAL,
    [ValidateSet('all', 'cold', 'gfx', 'open', 'type')]
    [string]$Scenario = 'all',
    [string]$Package = 'com.lioisme.lide',
    [double]$ColdStartLimitMs = 6000,
    [int]$JankLimit = 2
)

$ErrorActionPreference = 'Stop'
$adbExe = if (Get-Command adb -ErrorAction SilentlyContinue) { 'adb' } else {
    $p = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $p) { $p } else { throw 'adb not found in PATH or LOCALAPPDATA\Android\Sdk' }
}

function Invoke-Adb {
    param([string[]]$A)
    $argsToPass = @()
    if ($Serial) { $argsToPass += '--serial', $Serial }
    $argsToPass += $A
    & $adbExe @argsToPass 2>&1
}

if ((Invoke-Adb @('get-state')) -ne 'device') {
    throw "设备不可用（$Serial）——请检查 adb 连接 / ANDROID_SERIAL"
}

function Get-Metric {
    param([string]$Output, [string]$Metric)
    $line = $Output | Select-String -Pattern "${Metric}:\s+(\d+)" | Select-Object -First 1
    if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    return -1
}

function Get-GfxJank {
    param([string]$Pkg)
    $out = Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', $Pkg)
    $total = $out | Select-String -Pattern 'Total frames rendered:\s+(\d+)' | Select-Object -First 1
    $jank = $out | Select-String -Pattern 'Janky frames:\s+(\d+)' | Select-Object -First 1
    $t = if ($total) { [int]$total.Matches[0].Groups[1].Value } else { 0 }
    $j = if ($jank) { [int]$jank.Matches[0].Groups[1].Value } else { 0 }
    return @{ Total = $t; Janky = $j }
}

$results = [ordered]@{}

if ($Scenario -eq 'all' -or $Scenario -eq 'cold') {
    $v = @()
    foreach ($i in 1..3) {
        $o = Invoke-Adb @('shell', 'am', 'start', '-W', '-S', '-n', "$Package/.MainActivity")
        $t = Get-Metric -Output $o -Metric 'WaitTime'
        if ($t -gt 0) { $v += $t }
        Start-Sleep -Milliseconds 800
    }
    $coldMs = if ($v.Count -gt 0) { [int](($v | Sort-Object)[[math]::Floor($v.Count / 2)]) } else { -1 }
    $results['cold start median (ms)'] = $coldMs
}

if ($Scenario -eq 'all' -or $Scenario -eq 'gfx') {
    Invoke-Adb @('shell', 'am', 'start', '-n', "$Package/.MainActivity") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') | Out-Null
    for ($i = 0; $i -lt 8; $i++) {
        Invoke-Adb @('shell', 'input', 'swipe', '720', '1900', '720', '700', '300') | Out-Null
        Start-Sleep -Milliseconds 300
    }
    $g = Get-GfxJank -Pkg $Package
    $results['scroll janky/total'] = "$($g.Janky)/$($g.Total)"
}

if ($Scenario -eq 'all' -or $Scenario -eq 'open') {
    # 77KB 打开：生成工程 + 深链到一个非 main 的 .lua → 编辑器打开该文件（不触发引擎运行）
    $dir = Join-Path $env:TEMP 'lide-perf77'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $big = Join-Path $dir 'big.lua'
    if (-not (Test-Path $big) -or (Get-Item $big).Length -ne 77824) {
        $sb = New-Object System.Text.StringBuilder
        for ($i = 0; $i -lt 4000; $i++) {
            [void]$sb.Append("local v$i = function(n)`n    if n > 0 then return n - 1 else return n end`nend`n")
        }
        [System.IO.File]::WriteAllText($big, $sb.ToString())
    }
    Invoke-Adb @('push', $big, '/sdcard/AndroLua/project/Perf77/big.lua') | Out-Null
    $o = Invoke-Adb @('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'androlua://project/Perf77/big.lua')
    $w = Get-Metric -Output $o -Metric 'WaitTime'
    $results['open 77KB WaitTime (ms)'] = $w
}

if ($Scenario -eq 'all' -or $Scenario -eq 'type') {
    # 打开一个编辑器内文件，焦点进编辑器后连续输入 200 字符，读 gfxinfo jank
    Invoke-Adb @('shell', 'echo', '-- placeholder', '>', '/sdcard/AndroLua/project/FoldTest/other.lua') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'androlua://project/FoldTest/other.lua') | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb @('shell', 'input', 'tap', '720', '1400') | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') | Out-Null
    Invoke-Adb @('shell', 'input', 'text', ('a' * 200)) | Out-Null
    Start-Sleep -Seconds 1
    $g = Get-GfxJank -Pkg $Package
    $results['type 200 chars janky'] = $g.Janky
}

# ---- report ----
Write-Host ''
Write-Host ('==== lide perf-check @ ' + $(if ($Serial) { $Serial } else { 'default' }) + ' ====') -ForegroundColor Cyan
$ok = $true
foreach ($k in $results.Keys) {
    $v = $results[$k]
    $show = if ($null -eq $v -or $v -eq '') { 'N/A' } else { $v }
    Write-Host ("  {0,-28} {1}" -f $k, $show)
}
if ($results['cold start median (ms)'] -gt $ColdStartMs) {
    $ok = $false
    Write-Host '  FAIL: cold start over budget' -ForegroundColor Red
}
if (($results['scroll janky/total'] -split '/')[0] -as [int] -gt $JankLimit) {
    $ok = $false
    Write-Host '  FAIL: scroll jank over limit' -ForegroundColor Red
}
if (($results['type 200 chars janky'] -as [int]) -gt $JankLimit) {
    $ok = $false
    Write-Host '  FAIL: typing jank over limit' -ForegroundColor Red
}
Write-Host ''
if ($ok) { Write-Host 'RESULT: PASS' -ForegroundColor Green } else { Write-Host 'RESULT: FAIL' -ForegroundColor Red }
exit $(if ($ok) { 0 } else { 1 })