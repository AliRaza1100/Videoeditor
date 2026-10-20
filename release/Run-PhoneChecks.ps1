param([string]$Serial,[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$workspaceRoot=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sdkSetting=Get-Content -LiteralPath (Join-Path $workspaceRoot 'local.properties') | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if(!$sdkSetting) { throw 'Configure sdk.dir in local.properties before running phone checks.' }
$sdkRoot=$sdkSetting.Substring(8).Replace('\:',':').Replace('\\','\')
$adb=Join-Path $sdkRoot 'platform-tools/adb.exe'
$aapt=Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
$devices=@(& $adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
if(!$Serial) { if($devices.Count -ne 1) { throw 'Connect exactly one authorized Android phone, enable USB debugging and accept its RSA prompt; or supply -Serial.' }; $Serial=$devices[0] }
if($Serial -notin $devices) { throw 'The selected phone is not connected and authorized. Check the cable, USB driver and debugging prompt.' }
Push-Location -LiteralPath $workspaceRoot
try {
    if(!$SkipBuild) {
        & (Join-Path $workspaceRoot 'gradlew.bat') :app:assembleDebug :app:assembleDebugAndroidTest -PqaBuild=true --console=plain
        if($LASTEXITCODE -ne 0) { throw 'QA build failed.' }
        New-Item -ItemType Directory -Path verification/qa -Force | Out-Null
        Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination verification/qa/lumen-qa.apk -Force
        Copy-Item -LiteralPath app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk -Destination verification/qa/lumen-qa-tests.apk -Force
    }
    $qaApp=Join-Path $workspaceRoot 'verification/qa/lumen-qa.apk'
    $qaTests=Join-Path $workspaceRoot 'verification/qa/lumen-qa-tests.apk'
    $appInfo=(& $aapt dump badging $qaApp) -join "`n"
    $testInfo=(& $aapt dump badging $qaTests) -join "`n"
    if($appInfo -notmatch "package: name='com\.lumen\.studio\.qa' " -or $testInfo -notmatch "package: name='com\.lumen\.studio\.qa\.test' ") { throw 'Refusing to install anything except the isolated QA application and its QA tests.' }
    & $adb -s $Serial install -r -t $qaApp
    if($LASTEXITCODE -ne 0) { throw 'QA application install failed.' }
    & $adb -s $Serial install -r -t $qaTests
    if($LASTEXITCODE -ne 0) { throw 'QA tests install failed.' }
    $logDirectory=Join-Path $workspaceRoot 'verification/device-tests'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $result=@(& $adb -s $Serial shell am instrument -w -r -e class com.lumen.studio.PlayReadinessTest com.lumen.studio.qa.test/android.test.InstrumentationTestRunner 2>&1)
    $result | Set-Content -LiteralPath (Join-Path $logDirectory 'play-readiness.txt')
    $result | Write-Output
    if(($result -join "`n") -notmatch 'OK \(([1-9]\d*) tests?\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=') { throw 'Phone checks did not pass. Review verification/device-tests/play-readiness.txt.' }
    Write-Output 'QA phone checks passed. Inspect the app screens and generated sample, then run the remaining real-media/device matrix. Your production package was not cleared or replaced.'
} finally { Pop-Location }
