#requires -Version 7.0
<#
.SYNOPSIS
Collect repeatable TV startup timings or memory/frame snapshots from one device.
.DESCRIPTION
Startup force-stops and relaunches the selected package. Navigation and Playback
only observe the app: use the remote to perform the documented scenario.
No app data is cleared, settings changed, or APK installed by this script.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [string]$AdbPath = 'adb',
    [ValidatePattern('^[a-zA-Z0-9_.]+$')][string]$PackageName = 'com.music.bitchord.tv',
    [ValidateSet('Startup', 'Navigation', 'Playback')][string]$Scenario = 'Startup',
    [ValidateSet('baseline', 'candidate')][string]$Label = 'baseline',
    [ValidateRange(1, 100)][int]$LaunchCount = 30,
    [ValidateRange(5, 7200)][int]$DurationSeconds = 60,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../../output/performance')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-TvAdb {
    param([string[]]$Arguments)
    $result = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")" }
    return ($result -join "`n")
}

if ((Invoke-TvAdb -Arguments @('get-state')).Trim() -ne 'device') { throw 'Device is not ready or authorized.' }
$packageInfo = Invoke-TvAdb -Arguments @('shell', 'dumpsys', 'package', $PackageName)
if ($packageInfo -notmatch 'versionCode=') { throw "Package is not installed: $PackageName" }
$component = (Invoke-TvAdb -Arguments @(
    'shell', 'cmd', 'package', 'resolve-activity', '--brief',
    '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LEANBACK_LAUNCHER', $PackageName
)).Split("`n") | Where-Object { $_.Trim() -match '^[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+$' } | Select-Object -Last 1
if (-not $component) { throw "No TV launcher activity found for $PackageName" }
$component = $component.Trim()

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $OutputDirectory "$Label-$Scenario-$stamp"
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$device = [ordered]@{
    serial = $Serial
    model = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.product.model')).Trim()
    firmware = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.build.fingerprint')).Trim()
    android = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.build.version.release')).Trim()
    sdk = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
    abi = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.product.cpu.abilist')).Trim()
    lowRamFlag = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'ro.config.low_ram')).Trim()
    heapGrowthLimit = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'dalvik.vm.heapgrowthlimit')).Trim()
    heapSize = (Invoke-TvAdb -Arguments @('shell', 'getprop', 'dalvik.vm.heapsize')).Trim()
    packageName = $PackageName
    versionCode = [regex]::Match($packageInfo, '\bversionCode=(\d+)').Groups[1].Value
    versionName = [regex]::Match($packageInfo, '\bversionName=([^\s]+)').Groups[1].Value
    debuggable = $packageInfo -match '\bDEBUGGABLE\b'
    component = $component
    label = $Label
    scenario = $Scenario
    measuredAt = (Get-Date).ToString('o')
}
$device | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory 'device.json') -Encoding utf8
$packageInfo | Set-Content -LiteralPath (Join-Path $runDirectory 'package.txt') -Encoding utf8
Invoke-TvAdb -Arguments @('shell', 'cat', '/proc/meminfo') |
    Set-Content -LiteralPath (Join-Path $runDirectory 'system-memory.txt') -Encoding utf8
Invoke-TvAdb -Arguments @('shell', 'dumpsys', 'display') |
    Set-Content -LiteralPath (Join-Path $runDirectory 'display.txt') -Encoding utf8

if ($Scenario -eq 'Startup') {
    $samples = for ($index = 1; $index -le $LaunchCount; $index++) {
        $raw = Invoke-TvAdb -Arguments @('shell', 'am', 'start', '-W', '-S', '-n', $component)
        $raw | Set-Content -LiteralPath (Join-Path $runDirectory "startup-$index.txt") -Encoding utf8
        if ($raw -notmatch '(?m)^Status:\s*ok\s*$' -or $raw -notmatch '(?m)^TotalTime:\s*(\d+)\s*$') {
            throw "Launch $index did not produce a successful cold-start timing. See startup-$index.txt"
        }
        $totalMs = [int]$Matches[1]
        [pscustomobject]@{ iteration = $index; totalTimeMs = $totalMs }
        Start-Sleep -Seconds 3
    }
    $samples | Export-Csv -LiteralPath (Join-Path $runDirectory 'startup.csv') -NoTypeInformation -Encoding utf8
    $ordered = @($samples.totalTimeMs | Sort-Object)
    [ordered]@{
        count = $ordered.Count
        p50TotalTimeMs = $ordered[[math]::Ceiling($ordered.Count * 0.50) - 1]
        p95TotalTimeMs = $ordered[[math]::Ceiling($ordered.Count * 0.95) - 1]
        note = 'Activity launch TotalTime; does not measure network content readiness or first audio.'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory 'summary.json') -Encoding utf8
} else {
    Write-Host "Collecting $Scenario for $DurationSeconds seconds. Perform the scenario using the remote."
    Invoke-TvAdb -Arguments @('shell', 'dumpsys', 'gfxinfo', $PackageName, 'reset') |
        Set-Content -LiteralPath (Join-Path $runDirectory 'frames-before.txt') -Encoding utf8
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    $sample = 0
    while ($clock.Elapsed.TotalSeconds -lt $DurationSeconds) {
        Invoke-TvAdb -Arguments @('shell', 'dumpsys', 'meminfo', $PackageName) |
            Set-Content -LiteralPath (Join-Path $runDirectory "memory-$sample.txt") -Encoding utf8
        $sample++
        Start-Sleep -Seconds 5
    }
    Invoke-TvAdb -Arguments @('shell', 'dumpsys', 'gfxinfo', $PackageName, 'framestats') |
        Set-Content -LiteralPath (Join-Path $runDirectory 'frames-after.txt') -Encoding utf8
}
Write-Host "Saved: $runDirectory"
