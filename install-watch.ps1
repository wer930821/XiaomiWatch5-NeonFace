$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$log = Join-Path $PSScriptRoot 'install-log.txt'

try { Start-Transcript -Path $log -Force | Out-Null } catch {}

function Step([string]$msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }
function Fail([string]$msg) { throw $msg }

function Get-AdbDevices([string]$adbPath) {
    $raw = & $adbPath devices 2>$null
    $list = @()
    foreach ($line in $raw) {
        if ($line -match '^(.+?)\s+device$') {
            $serial = $Matches[1].Trim()
            if ($serial) { $list += $serial }
        }
    }
    return @($list)
}

function Select-WatchTarget([object[]]$devices) {
    if (-not $devices -or $devices.Count -eq 0) { return $null }

    $target = $devices | Where-Object { $_ -match '^\d{1,3}(\.\d{1,3}){3}:\d+$' } | Select-Object -First 1
    if ($target) { return $target }

    $target = $devices | Where-Object { $_ -match '_adb-tls-connect\._tcp$' -and $_ -notmatch '\(\d+\)' } | Select-Object -First 1
    if ($target) { return $target }

    $target = $devices | Where-Object { $_ -match '_adb-tls-connect\._tcp$' } | Select-Object -First 1
    if ($target) { return $target }

    return ($devices | Select-Object -First 1)
}

function Try-MdnsReconnect([string]$adbPath) {
    try {
        $services = & $adbPath mdns services 2>$null
        foreach ($line in $services) {
            if ($line -match '_adb-tls-connect\._tcp' -and $line -match '(\d{1,3}(?:\.\d{1,3}){3}:\d+)') {
                $endpoint = $Matches[1]
                Write-Host "Trying mDNS endpoint: $endpoint"
                & $adbPath connect $endpoint 2>$null | Out-Host
                Start-Sleep -Seconds 2
                $devices = Get-AdbDevices $adbPath
                $target = Select-WatchTarget $devices
                if ($target) { return $target }
            }
        }
    } catch {}
    return $null
}

function Refresh-WatchTarget([string]$adbPath, [int]$timeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        $devices = Get-AdbDevices $adbPath
        $target = Select-WatchTarget $devices
        if ($target) { return $target }

        $target = Try-MdnsReconnect $adbPath
        if ($target) { return $target }

        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    return $null
}

try {
    Set-Location $PSScriptRoot

    Step 'Checking Android SDK'
    $sdkCandidates = @()
    if ($env:ANDROID_HOME) { $sdkCandidates += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $sdkCandidates += $env:ANDROID_SDK_ROOT }
    if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
    $sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $sdk) { Fail 'Android SDK not found. Install it from Android Studio SDK Manager.' }
    $sdkForProperties = $sdk.Replace('\','\\')
    "sdk.dir=$sdkForProperties" | Set-Content -Encoding ASCII -Path (Join-Path $PSScriptRoot 'local.properties')
    Write-Host "SDK: $sdk"

    Step 'Finding ADB'
    $adb = $null
    $candidate = Join-Path $sdk 'platform-tools\adb.exe'
    if (Test-Path $candidate) { $adb = $candidate }
    if (-not $adb) {
        $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($cmd) { $adb = $cmd.Source }
    }
    if (-not $adb) { Fail 'adb.exe not found. Install Android SDK Platform-Tools.' }
    Write-Host "ADB: $adb"

    Step 'Checking connected watch'
    $target = Refresh-WatchTarget $adb 15
    if (-not $target) { Fail 'No ADB device is connected. Enable Wireless debugging on the watch and connect it first.' }
    Write-Host "Target before build: $target" -ForegroundColor Green

    try { & $adb -s $target shell input keyevent KEYCODE_WAKEUP 2>$null | Out-Null } catch {}

    Step 'Building watch face'
    & (Join-Path $PSScriptRoot 'gradlew.bat') ':watchface:assembleDebug'
    if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed with exit code $LASTEXITCODE." }

    $apk = Join-Path $PSScriptRoot 'watchface\build\outputs\apk\debug\watchface-debug.apk'
    if (-not (Test-Path $apk)) { Fail "APK not found: $apk" }
    Write-Host "APK: $apk"

    Step 'Refreshing watch connection after build'
    $target = Refresh-WatchTarget $adb 45
    if (-not $target) {
        Fail 'The watch disconnected during the build. Keep Wireless debugging open on the watch, then run INSTALL_WATCH.cmd again.'
    }
    Write-Host "Target for install: $target" -ForegroundColor Green

    Step 'Installing to Xiaomi Watch 5'
    & $adb -s $target install -r $apk
    $installCode = $LASTEXITCODE

    if ($installCode -ne 0) {
        Write-Host 'First install attempt failed. Refreshing ADB target and retrying once...' -ForegroundColor Yellow
        $target2 = Refresh-WatchTarget $adb 30
        if ($target2) {
            Write-Host "Retry target: $target2" -ForegroundColor Green
            & $adb -s $target2 install -r $apk
            $installCode = $LASTEXITCODE
            if ($installCode -eq 0) { $target = $target2 }
        }
    }

    if ($installCode -ne 0) { Fail "ADB install failed with exit code $installCode." }

    Step 'Verifying package'
    $pkg = 'com.agoose.xiaomiwatch5.neonface'
    $verify = & $adb -s $target shell pm path $pkg 2>$null
    if ($verify -match '^package:') {
        Write-Host 'SUCCESS: Watch face is installed.' -ForegroundColor Green
        Write-Host 'Long-press the current watch face, then add/select Neon Core Blue.'
    } else {
        Write-Host 'Install command succeeded, but package verification returned no path.' -ForegroundColor Yellow
        Write-Host 'Check the watch face picker on the watch.'
    }
    exit 0
}
catch {
    Write-Host ''
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    if ($_.ScriptStackTrace) { Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray }
    exit 1
}
finally {
    try { Stop-Transcript | Out-Null } catch {}
}
