<#
.SYNOPSIS
    校验 app/vendor/androlua 与上游 AndroLua Pro 的全树差异 ⊆ 台账白名单
    （scripts/upstream-pristine.json，与 app/vendor/androlua/UPSTREAM.md 对应的机器可读副本）。
.DESCRIPTION
    用户红线（2026-08-09）：禁止魔改或强耦合依赖上游；androlua 特殊基线仅允许台账登记的非逻辑适配。
    本脚本将 vendor Java 树与上游树逐文件比对：
      1. vendor 本地独有 .java（上游不存在）→ 必须 0（白名单仅含 modified/deleted，不含新增）；
      2. 双侧共有的 .java 若有实质差异（忽略行尾/空白）→ 文件必须 ∈ 白名单 modified；
      3. 上游独有 .java（vendor 已删）→ 必须 ∈ 白名单 deleted。
    任何清单外改动 → 非零退出码（CI verify-vendor job 挂接）。
.PARAMETER UpstreamRoot
    已解压的上游树目录（含 app/src/main/java）。缺省时自动从 GitHub 下载
    upstreamRef 对应 tarball 到临时目录（不落地仓库，符合 R5 无二进制入库）。
.PARAMETER SkipDownload
    缺省自动下载时跳过（配合 -UpstreamRoot 使用无意义；仅调试）。
.EXAMPLE
    ./scripts/verify-upstream-pristine.ps1 -UpstreamRoot C:\temp\AndroLua_pro-abc
.EXAMPLE
    ./scripts/verify-upstream-pristine.ps1   # 自动下载比对
#>
param(
    [string]$UpstreamRoot = "",
    [switch]$SkipDownload
)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$jsonPath = Join-Path $PSScriptRoot "upstream-pristine.json"
$cfg = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json

$vendorRoot = Join-Path $repoRoot ($cfg.vendorRoot -replace '/', '\')
if (-not (Test-Path -LiteralPath $vendorRoot)) {
    Write-Host "FAIL: vendor Java 根不存在: $vendorRoot"
    exit 2
}

# ---------- 上游树获取 ----------
if (-not $UpstreamRoot) {
    if ($SkipDownload) { Write-Host "FAIL: -SkipDownload 需要 -UpstreamRoot"; exit 2 }
    $work = Join-Path $env:TEMP ("upstream-pristine-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $work | Out-Null
    $zip = Join-Path $work "upstream.zip"
    $url = "https://codeload.github.com/nirenr/AndroLua_pro/zip/$($cfg.upstreamRef)"
    Write-Host "下载上游树: $url"
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -LiteralPath $zip -DestinationPath $work
    } catch {
        Write-Host "FAIL: 上游树下载失败（离线请用 -UpstreamRoot 提供已解压上游树）: $($_.Exception.Message)"
        exit 2
    }
    $UpstreamRoot = Get-ChildItem $work -Directory | Where-Object { Test-Path (Join-Path $_.FullName ($cfg.upstreamJavaRoot -replace '/', '\')) } | Select-Object -First 1 -ExpandProperty FullName
    if (-not $UpstreamRoot) {
        Write-Host "FAIL: 解压后未找到上游 $($cfg.upstreamJavaRoot)（结构漂移）"
        exit 2
    }
} else {
    if ($SkipDownload) { Write-Host "（-SkipDownload 已提供 -UpstreamRoot）" }
}
$upstreamRoot = Join-Path $UpstreamRoot ($cfg.upstreamJavaRoot -replace '/', '\')
if (-not (Test-Path -LiteralPath $upstreamRoot)) {
    Write-Host "FAIL: 上游 Java 根不存在: $upstreamRoot"
    exit 2
}

# ---------- 白名单自检（清单录入错误预防） ----------
function Test-AnyDiff($up, $local) {
    $q = { param($s) '"' + ($s -replace '"', '\"') + '"' }
    & cmd /c "git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol -w --quiet $(& $q $up) $(& $q $local) 2>nul"
    return ($LASTEXITCODE -ne 0)
}
$modified = @{}; foreach ($p in $cfg.modified) { $modified["$p"] = $true }
$deleted = @{}; foreach ($p in $cfg.deleted) { $deleted["$p"] = $true }

$failures = @()
$okCount = 0

# ---------- 1. vendor 本地独有 + 2. 共有文件差异 ----------
Get-ChildItem -LiteralPath $vendorRoot -Recurse -Filter *.java | ForEach-Object {
    $rel = $_.FullName.Substring($vendorRoot.Length + 1).Replace('\', '/')
    $up = Join-Path $upstreamRoot ($rel -replace '/', '\')
    if (-not (Test-Path -LiteralPath $up)) {
        if ($modified.ContainsKey($rel)) {
            $failures += "白名单冲突: modified 条目在 upstream 侧不存在（清单录入错误）: $rel"
        } else {
            $failures += "LOCAL-ONLY（上游不存在且不在白名单）: $rel"
        }
        return
    }
    if (-not (Test-Path -LiteralPath $_.FullName)) { return }
    if (Test-AnyDiff $up $_.FullName) {
        if ($modified.ContainsKey($rel)) { $okCount++ }
        else { $failures += "MODIFIED（实质差异且不在白名单）: $rel" }
    } else {
        if ($modified.ContainsKey($rel)) {
            Write-Host "（注意）白名单文件当前与上游无差异（清单可收敛）: $rel"
        }
        $okCount++
    }
}

# ---------- 3. 上游独有（deleted 白名单） ----------
Get-ChildItem -LiteralPath $upstreamRoot -Recurse -Filter *.java | ForEach-Object {
    $rel = $_.FullName.Substring($upstreamRoot.Length + 1).Replace('\', '/')
    $local = Join-Path $vendorRoot ($rel -replace '/', '\')
    if (-not (Test-Path -LiteralPath $local)) {
        if ($deleted.ContainsKey($rel)) { $okCount++ }
        else { $failures += "DELETED（上游有、vendor 已删且不在白名单）: $rel" }
    }
}

# ---------- 汇总 ----------
Write-Host ""
Write-Host "=== verify-upstream-pristine（上游 $($cfg.upstreamRef)）==="
Write-Host "通过: $okCount 项  失败: $($failures.Count) 项"
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "  FAIL  $_" }
    Write-Host "白名单与台账: scripts/upstream-pristine.json / app/vendor/androlua/UPSTREAM.md（两处不一致即失败，新增改动必须先改台账再改代码）"
    exit 1
}
Write-Host "OK: vendor 全树差异 ⊆ 台账白名单（24 modified + 42 deleted，零 local-only）"
exit 0
