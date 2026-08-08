#Requires -Version 5.1
<#
.SYNOPSIS
  sora-editor 上游更新脚本（P3 §1）。零魔改、零耦合：整体替换两模块 src/main，
  仅保留白名单文件；依赖版本从上游 gradle/libs.versions.toml 同步。
.DESCRIPTION
  流程（对应 third_party/sora-editor/README.md「更新流程」）：
    1. 解析当前版本（third_party/sora-editor/VERSION）
    2. 目标版本：-Tag 指定，或省略时自动查 GitHub 最新 release
    3. 下载 tag zip 并展开
    4. 白名单备份：editor/build.gradle.kts、language-textmate/build.gradle.kts、
       language-textmate/src/main/java/io/github/rosemoe/oniguruma/OnigNative.java（桩）
    5. 整体替换 editor/src/main 与 language-textmate/src/main
    6. 恢复白名单文件
    7. 同步依赖版本（上游 toml -> 两模块 build.gradle.kts）
    8. 写 VERSION；默认跑本地门禁（:app:compileArm64DebugKotlin + :app:testArm64DebugUnitTest）
.EXAMPLE
  .\scripts\update-sora-editor.ps1 -Tag 0.25.0
  .\scripts\update-sora-editor.ps1              # 自动取最新 release
  .\scripts\update-sora-editor.ps1 -Tag 0.25.0 -SkipBuild
#>
param(
    [string]$Tag = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $root 'third_party\sora-editor'
$versionFile = Join-Path $target 'VERSION'

if (-not (Test-Path $versionFile)) { throw "缺少版本台账文件：$versionFile" }
$current = (Get-Content $versionFile -Raw).Trim()

$repo = 'Rosemoe/sora-editor'
if (-not $Tag) {
    $latest = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'lide-update-sora' }
    $Tag = $latest.tag_name
    Write-Host "自动检测到最新 release：$Tag"
}
if ($Tag -eq $current) {
    Write-Host "已是当前版本 $Tag，无需更新。"
    exit 0
}
Write-Host "更新 sora-editor：$current -> $Tag"

# ---- 下载展开 ----
$work = Join-Path $env:TEMP "sora-update-$Tag"
$zip = "$work.zip"
if (-not (Test-Path $zip)) {
    Write-Host "下载 https://github.com/$repo/archive/refs/tags/$Tag.zip ..."
    Invoke-WebRequest "https://github.com/$repo/archive/refs/tags/$Tag.zip" -OutFile $zip
}
if (-not (Test-Path $work)) { Expand-Archive -Path $zip -DestinationPath $work -Force }
$src = Join-Path $work "sora-editor-$Tag"
if (-not (Test-Path (Join-Path $src 'editor\src\main'))) { throw "归档目录结构异常：$src" }

# ---- 白名单备份 ----
$wl = @(
    @{ Path = 'editor\build.gradle.kts'; Reuse = $true },
    @{ Path = 'language-textmate\build.gradle.kts'; Reuse = $true },
    @{ Path = 'language-textmate\src\main\java\io\github\rosemoe\oniguruma\OnigNative.java'; Reuse = $false }
)
$bakDir = Join-Path $env:TEMP "sora-wl-$(Get-Random)"
New-Item -ItemType Directory -Force -Path $bakDir | Out-Null
foreach ($f in $wl) {
    $p = Join-Path $target $f.Path
    if (-not (Test-Path $p)) { Write-Warning "白名单文件缺失（首次升级时忽略）：$($f.Path)"; continue }
    $rel = $f.Path.Replace('\', '__')
    Copy-Item $p (Join-Path $bakDir $rel)
    Write-Host "备份白名单：$($f.Path)"
}

# ---- 整体替换 src/main ----
foreach ($mod in @('editor', 'language-textmate')) {
    $our = Join-Path $target "$mod\src\main"
    $up = Join-Path $src "$mod\src\main"
    if (-not (Test-Path $up)) { throw "上游缺少模块：$mod" }
    if (Test-Path $our) { Remove-Item $our -Recurse -Force }
    Copy-Item $up $our -Recurse -Force
    Write-Host "替换完成：$mod/src/main"
}

# ---- 恢复白名单 ----
foreach ($f in $wl) {
    $rel = $f.Path.Replace('\', '__')
    $bak = Join-Path $bakDir $rel
    if (Test-Path $bak) {
        $dest = Join-Path $target $f.Path
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        Copy-Item $bak $dest -Force
        Write-Host "恢复白名单：$($f.Path)"
    }
}

# ---- 依赖版本同步（上游 toml -> 两模块 build.gradle.kts） ----
$toml = Join-Path $src 'gradle\libs.versions.toml'
if (-not (Test-Path $toml)) { Write-Warning "上游缺少 gradle/libs.versions.toml，跳过依赖同步（请手工核对）" }
else {
    $tomlText = Get-Content $toml -Raw
    # artifact 全名 -> toml 中的版本 key（或内联版本）
    $map = @(
        @{ Artifact = 'androidx.annotation:annotation'; Key = 'annotation' },
        @{ Artifact = 'androidx.collection:collection'; Key = 'collection' },
        @{ Artifact = 'com.google.code.gson:gson'; Key = 'gson' },
        @{ Artifact = 'org.jruby.jcodings:jcodings'; Key = 'jcodings' },
        @{ Artifact = 'org.jruby.joni:joni'; Key = 'joni' },
        @{ Artifact = 'org.snakeyaml:snakeyaml-engine'; Key = 'snakeyamlEngine' },
        @{ Artifact = 'org.eclipse.jdt:org.eclipse.jdt.annotation'; Key = 'jdt' }
    )
    foreach ($m in $map) {
        $ver = $null
        $kv = Select-String -InputObject $tomlText -Pattern "(?m)^$($m.Key)\s*=\s*`"([^`"]+)`"" | Select-Object -First 1
        if ($kv) { $ver = $kv.Matches[0].Groups[1].Value }
        if (-not $ver) {
            $inline = Select-String -InputObject $tomlText -Pattern "$($m.Artifact):\s*([^\s`"]+)" | Select-Object -First 1
            if ($inline) { $ver = $inline.Matches[0].Groups[1].Value }
        }
        if (-not $ver) { Write-Warning "toml 未找到版本 key '$($m.Key)'，跳过：$($m.Artifact)"; continue }
        foreach ($bf in @((Join-Path $target 'editor\build.gradle.kts'), (Join-Path $target 'language-textmate\build.gradle.kts'))) {
            $text = Get-Content $bf -Raw
            $pattern = [regex]::Escape($m.Artifact + ':') + '\d[\d.]*(?:-[A-Za-z0-9.]+)?'
            if ($text -match $pattern) {
                $new = $text -replace $pattern, ($m.Artifact + ':' + $ver)
                [System.IO.File]::WriteAllText($bf, $new)
                Write-Host "同步版本：$($m.Artifact) -> $ver（$([System.IO.Path]::GetFileName($bf))）"
            }
        }
    }
}

# ---- 写版本台账 ----
[System.IO.File]::WriteAllText($versionFile, $Tag + [Environment]::NewLine)
Write-Host "VERSION：$current -> $Tag"

# ---- 门禁 ----
if (-not $SkipBuild) {
    Write-Host '运行门禁：:app:compileArm64DebugKotlin + :app:testArm64DebugUnitTest ...'
    & (Join-Path $root 'gradlew.bat') :app:compileArm64DebugKotlin --console=plain
    if ($LASTEXITCODE -ne 0) { Write-Host 'FAIL: 编译失败' -ForegroundColor Red; exit 1 }
    & (Join-Path $root 'gradlew.bat') :app:testArm64DebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { Write-Host 'FAIL: 单测失败' -ForegroundColor Red; exit 1 }
    Write-Host 'OK: 门禁通过' -ForegroundColor Green
}
Write-Host "完成。记得在 third_party/sora-editor/README.md 台账登记（版本/日期/PR 号/变更摘要）。"
exit 0