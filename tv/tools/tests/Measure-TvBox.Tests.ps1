#requires -Version 7.0
# Fixture tests for the collector only; these are NOT device performance results.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$collector = Join-Path $PSScriptRoot '../Measure-TvBox.ps1'
$testRoot = Join-Path $PSScriptRoot "../../../output/performance/collector-tests-$([guid]::NewGuid().ToString('N'))"
$fixtureState = [pscustomobject]@{
    Case = ''
    LaunchIndex = 0
    Commands = [System.Collections.Generic.List[string]]::new()
}

function Invoke-FixtureAdb {
    if ($args[0] -ne '-s' -or $args[1] -ne 'fixture-box') { throw 'Collector lost the explicit device serial.' }
    $command = $args[2..($args.Count - 1)] -join ' '
    $fixtureState.Commands.Add($command)
    $global:LASTEXITCODE = 0
    switch -Regex ($command) {
        '^get-state$' { if ($fixtureState.Case -eq 'unauthorized') { return 'unauthorized' }; return 'device' }
        '^shell dumpsys package ' { return 'versionCode=26 versionName=1.8 flags=[ DEBUGGABLE HAS_CODE ]' }
        '^shell cmd package resolve-activity ' { return 'com.music.bitchord.tv/com.music.bitchord.MainActivity' }
        '^shell getprop ' { return 'fixture' }
        '^shell cat /proc/meminfo$' { return 'MemTotal: 1850000 kB' }
        '^shell dumpsys display$' { return '1920x1080 60Hz fixture' }
        '^shell am start -W -S -n ' {
            if ($fixtureState.Case -eq 'failed-launch') { return 'Error: Activity not found' }
            if ($fixtureState.Case -eq 'adb-error') { $global:LASTEXITCODE = 1; return 'error: disconnected' }
            $timing = @(300, 100, 140)[$fixtureState.LaunchIndex]
            $fixtureState.LaunchIndex++
            return "Status: ok`r`nTotalTime: $timing`r`nWaitTime: 350"
        }
        '^shell dumpsys gfxinfo ' { return 'fixture frame report' }
        '^shell dumpsys meminfo ' { return 'TOTAL PSS: 120000' }
        default { throw "Unexpected collector command: $command" }
    }
}

function Assert-Collector {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Invoke-ExpectedFailure {
    param([string]$Fixture, [string]$ExpectedMessage)
    $fixtureState.Case = $Fixture
    $failed = $false
    try {
        & $collector -Serial fixture-box -AdbPath Invoke-FixtureAdb -LaunchCount 1 `
            -OutputDirectory (Join-Path $testRoot $Fixture)
    } catch {
        $failed = $true
        Assert-Collector ($_.Exception.Message -like "*$ExpectedMessage*") "Unexpected failure: $_"
    }
    Assert-Collector $failed "Collector accepted fixture $Fixture as a valid measurement."
}

$fixtureState.Case = 'startup'
& $collector -Serial fixture-box -AdbPath Invoke-FixtureAdb -LaunchCount 3 `
    -OutputDirectory (Join-Path $testRoot 'startup')
$summaryFile = Get-ChildItem (Join-Path $testRoot 'startup') -Filter summary.json -Recurse | Select-Object -First 1
$summary = Get-Content -LiteralPath $summaryFile.FullName -Raw | ConvertFrom-Json
Assert-Collector ($summary.count -eq 3 -and $summary.p50TotalTimeMs -eq 140 -and $summary.p95TotalTimeMs -eq 300) `
    'Startup sample count or nearest-rank percentiles are incorrect.'
$csvFile = Get-ChildItem (Join-Path $testRoot 'startup') -Filter startup.csv -Recurse | Select-Object -First 1
Assert-Collector (@(Import-Csv -LiteralPath $csvFile.FullName).Count -eq 3) 'Raw startup samples were lost.'
$deviceFile = Get-ChildItem (Join-Path $testRoot 'startup') -Filter device.json -Recurse | Select-Object -First 1
$deviceInfo = Get-Content -LiteralPath $deviceFile.FullName -Raw | ConvertFrom-Json
Assert-Collector ($deviceInfo.versionCode -eq '26' -and $deviceInfo.versionName -eq '1.8' -and $deviceInfo.debuggable) `
    'Collector did not preserve version/build-type information needed for a fair comparison.'

Invoke-ExpectedFailure -Fixture unauthorized -ExpectedMessage 'not ready or authorized'
Invoke-ExpectedFailure -Fixture failed-launch -ExpectedMessage 'did not produce a successful cold-start timing'
Invoke-ExpectedFailure -Fixture adb-error -ExpectedMessage 'ADB failed'

$fixtureState.Case = 'navigation'
$fixtureState.Commands.Clear()
& $collector -Serial fixture-box -AdbPath Invoke-FixtureAdb -Scenario Navigation -DurationSeconds 5 `
    -OutputDirectory (Join-Path $testRoot 'navigation')
Assert-Collector (@($fixtureState.Commands | Where-Object { $_ -match '^shell am ' }).Count -eq 0) `
    'Navigation observation must not restart or control the app.'
Assert-Collector ($fixtureState.Commands.Contains('shell dumpsys gfxinfo com.music.bitchord.tv framestats')) `
    'Navigation observation did not collect the final frame report.'
Assert-Collector ($fixtureState.Commands.Contains('shell dumpsys meminfo com.music.bitchord.tv')) `
    'Navigation observation did not collect app memory.'

Write-Host 'PASS: 5 collector fixture scenarios (startup, unauthorized, failed launch, ADB error, navigation).'
