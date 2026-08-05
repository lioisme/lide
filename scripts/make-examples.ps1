# make-examples.ps1
# Regenerates the built-in .alp example files under app/src/main/assets/examples/
# from the canonical source trees under scripts/examples-src/.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\make-examples.ps1
# Output: one .alp per example dir, named after the dir (the target project
#         directory name is resolved at import time from init.lua appname).
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$srcRoot = Join-Path $PSScriptRoot "examples-src"
$outRoot = Join-Path $PSScriptRoot "..\app\src\main\assets\examples"
$outRoot = [System.IO.Path]::GetFullPath($outRoot)

if (-not (Test-Path -LiteralPath $srcRoot)) {
    Write-Error "source root not found: $srcRoot"
}
if (-not (Test-Path -LiteralPath $outRoot)) {
    New-Item -ItemType Directory -Path $outRoot | Out-Null
}

function New-Alp([string]$srcDir, [string]$outPath) {
    if (Test-Path -LiteralPath $outPath) {
        Remove-Item -LiteralPath $outPath -Force
    }
    $zip = [System.IO.Compression.ZipFile]::Open($outPath, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $files = Get-ChildItem -LiteralPath $srcDir -Recurse -File | Sort-Object FullName
        foreach ($f in $files) {
            $rel = $f.FullName.Substring($srcDir.Length + 1).Replace('\', '/')
            $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
            $es = $entry.Open()
            try {
                $fs = [System.IO.File]::OpenRead($f.FullName)
                try { $fs.CopyTo($es) } finally { $fs.Dispose() }
            } finally {
                $es.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
}

$count = 0
Get-ChildItem -LiteralPath $srcRoot -Directory | Sort-Object Name | ForEach-Object {
    $outPath = Join-Path $outRoot ($_.Name + ".alp")
    New-Alp $_.FullName $outPath
    Write-Host ("built: " + $outPath)
    $count++
}
Write-Host ("done: $count .alp file(s)")
