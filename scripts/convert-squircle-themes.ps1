# One-time migration: Squircle CE theme JSON (VS Code format) -> TextMate settings format
#
# Purpose (2026-08-13 SQUIRCLE_ALIGNMENT_FOUR_FIX T1):
#   User decision: editor highlighting must match Squircle CE exactly -> import 9 themes.
#   LIDE pipeline (TextMateRuntime -> ThemeModel -> sora TextMateColorScheme.applyTMTheme)
#   consumes TextMate settings format; this script converts Squircle's
#   VSCode colors + tokenColors into equivalent TextMate format (tokenColors kept as-is).
#
# Outputs:
#   app/src/main/assets/textmate/<name>.json (9 files, UTF-8 no BOM)
#   Also copies raw Squircle JSON into app/src/test/resources/squircle-themes/
#   (test baseline: EditorPaletteContrastTest asserts parity, CI has no reference/)
#
# Color key mapping (mirrors sora TextMateColorScheme.applyTMTheme + EditorPaletteContrastTest):
#   editor.foreground            -> foreground
#   editor.background            -> background
#   editor.cursor                -> caret
#   editor.selection.background  -> selection
#   editor.currentLine.background -> lineHighlight
#   editor.whitespace            -> invisibles
#   editor.lineNumber.foreground -> lineNumber
#   editor.lineNumber.activeForeground -> lineNumberActiveForeground
#   editor.lineNumber.divider    -> lineNumberDivider
#   editor.indentGuide.background -> indentGuideBackground
#   editor.indentGuide.activeBackground -> indentGuideActiveBackground
#   editor.highlightedDelimiters.foreground -> highlightedDelimitersForeground
#   editor.highlightedDelimiters.background -> highlightedDelimitersBackground
#   editor.matchHighlight.background -> findMatchBackground
#   editor.popupWindow.background -> completionWindowBackground + tooltipBackground
#   editor.popupWindow.activeBackground -> completionWindowBackgroundCurrent
#   dropped: global.* (Squircle own UI chrome; LIDE layering uses host M3),
#            editor.handle / editor.lineNumber.background / editor.popupWindow.corner
#
# NOTE: keep this file ASCII-only (PowerShell 5.1 misreads UTF-8-no-BOM as ANSI).

param(
    [string]$SourceDir = "reference/squircle-ce-master/feature-themes/impl/src/main/assets/themes",
    [string]$OutDir = "app/src/main/assets/textmate",
    [string]$TestBaselineDir = "app/src/test/resources/squircle-themes"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Resolve-RelativeOrAbsolute([string]$path) {
    if ([System.IO.Path]::IsPathRooted($path)) { return $path }
    return (Join-Path $root $path)
}

$src = Resolve-RelativeOrAbsolute $SourceDir
$out = Resolve-RelativeOrAbsolute $OutDir
$baseline = Resolve-RelativeOrAbsolute $TestBaselineDir

if (-not (Test-Path $src)) { throw "Squircle themes source dir missing: $src" }
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $baseline | Out-Null

# Squircle colors key -> TextMate settings[0].settings keys (one value may feed several)
$keyMap = [ordered]@{
    "editor.background"                  = @("background")
    "editor.foreground"                  = @("foreground")
    "editor.cursor"                      = @("caret")
    "editor.selection.background"        = @("selection")
    "editor.currentLine.background"      = @("lineHighlight")
    "editor.whitespace"                  = @("invisibles")
    "editor.lineNumber.foreground"       = @("lineNumber")
    "editor.lineNumber.activeForeground" = @("lineNumberActiveForeground")
    "editor.lineNumber.divider"          = @("lineNumberDivider")
    "editor.indentGuide.background"      = @("indentGuideBackground")
    "editor.indentGuide.activeBackground" = @("indentGuideActiveBackground")
    "editor.highlightedDelimiters.foreground" = @("highlightedDelimitersForeground")
    "editor.highlightedDelimiters.background" = @("highlightedDelimitersBackground")
    "editor.matchHighlight.background"   = @("findMatchBackground")
    "editor.popupWindow.background"      = @("completionWindowBackground", "tooltipBackground")
    "editor.popupWindow.activeBackground" = @("completionWindowBackgroundCurrent")
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$files = @(Get-ChildItem -Path $src -Filter "*.json" | Sort-Object Name)
if ($files.Count -eq 0) { throw "no json themes found in $src" }

foreach ($file in $files) {
    $theme = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
    $name = $file.BaseName

    # 1) settings[0].settings from colors section
    $globalSettings = [ordered]@{}
    foreach ($entry in $keyMap.GetEnumerator()) {
        $value = $theme.colors.($entry.Key)
        if ($null -ne $value) {
            foreach ($target in $entry.Value) {
                $globalSettings[$target] = $value
            }
        }
    }

    # 2) tokenColors: skip first entry without scope (global bg/fg already in settings[0])
    $rules = New-Object System.Collections.Generic.List[object]
    foreach ($rule in $theme.tokenColors) {
        $scope = $rule.scope
        if ($null -eq $scope -or $scope -eq "") { continue }
        $item = [ordered]@{}
        if ($null -ne $rule.name -and [string]$rule.name -ne "") { $item["name"] = [string]$rule.name }
        if ($scope -is [array]) { $item["scope"] = @([string[]]$scope) } else { $item["scope"] = [string]$scope }
        $entrySettings = [ordered]@{}
        if ($null -ne $rule.settings.foreground) { $entrySettings["foreground"] = [string]$rule.settings.foreground }
        if ($null -ne $rule.settings.fontStyle -and [string]$rule.settings.fontStyle -ne "") {
            $entrySettings["fontStyle"] = [string]$rule.settings.fontStyle
        }
        $item["settings"] = $entrySettings
        $rules.Add($item)
    }

    $settingsList = New-Object System.Collections.Generic.List[object]
    $settingsList.Add([ordered]@{ settings = $globalSettings })
    foreach ($r in $rules) { $settingsList.Add($r) }

    $rootObj = [ordered]@{
        name = $name
        settings = $settingsList
    }

    $json = $rootObj | ConvertTo-Json -Depth 100
    $outPath = Join-Path $out "$name.json"
    [System.IO.File]::WriteAllText($outPath, $json, $utf8NoBom)
    Write-Host "converted -> $outPath"

    # 3) raw JSON into test baseline
    [System.IO.File]::Copy($file.FullName, (Join-Path $baseline "$name.json"), $true)
}

Write-Host "done. baseline -> $baseline"