Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName Microsoft.VisualBasic

[System.Windows.Forms.Application]::EnableVisualStyles()
$repoRoot = $PSScriptRoot

function Find-Adb {
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }
    if ($env:LOCALAPPDATA) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
        if (Test-Path $sdkAdb) { return $sdkAdb }
    }
    return $null
}

function Write-Log([string]$message) {
    if ([string]::IsNullOrWhiteSpace($message)) { return }
    $stamp = Get-Date -Format 'HH:mm:ss'
    $logBox.AppendText(('[' + $stamp + '] ' + $message + [Environment]::NewLine))
    $logBox.SelectionStart = $logBox.TextLength
    $logBox.ScrollToCaret()
    [System.Windows.Forms.Application]::DoEvents()
}

function Run-Command([string]$file, [string[]]$arguments) {
    try {
        Push-Location $repoRoot
        $lines = & $file @arguments 2>&1
        $code = $LASTEXITCODE
        $output = ($lines | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
        return [pscustomobject]@{ Code=$code; Out=$output; Err='' }
    }
    catch {
        return [pscustomobject]@{ Code=1; Out=''; Err=$_.Exception.Message }
    }
    finally {
        Pop-Location
    }
}

function Get-WatchStatus {
    $adb = Find-Adb
    if (-not $adb) {
        $watchStatus.Text = 'Watch: ADB not found'
        $watchStatus.ForeColor = [System.Drawing.Color]::IndianRed
        return $false
    }

    $result = Run-Command $adb @('devices')
    $device = $null
    foreach ($line in ($result.Out -split [Environment]::NewLine)) {
        if ($line -match '^(.+?)\s+device$') {
            $device = $Matches[1].Trim()
            break
        }
    }

    if ($device) {
        $watchStatus.Text = 'Watch: connected  ' + $device
        $watchStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        return $true
    }

    $watchStatus.Text = 'Watch: not connected'
    $watchStatus.ForeColor = [System.Drawing.Color]::OrangeRed
    return $false
}

function Check-Update {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        Write-Log 'Git was not found.'
        return
    }

    Write-Log 'Checking GitHub for updates...'
    $fetch = Run-Command 'git.exe' @('fetch','origin','main')
    if ($fetch.Code -ne 0) {
        Write-Log ('Update check failed: ' + $fetch.Err + ' ' + $fetch.Out)
        return
    }

    $local = Run-Command 'git.exe' @('rev-parse','HEAD')
    $remote = Run-Command 'git.exe' @('rev-parse','origin/main')

    if ($local.Out.Trim() -eq $remote.Out.Trim()) {
        $updateStatus.Text = 'App: up to date'
        $updateStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        Write-Log 'Already up to date.'
    }
    else {
        $updateStatus.Text = 'App: update available'
        $updateStatus.ForeColor = [System.Drawing.Color]::DeepSkyBlue
        Write-Log 'A new version is available.'
    }
}

function Update-App {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        Write-Log 'Git was not found.'
        return $false
    }

    Write-Log 'Updating from GitHub...'
    $result = Run-Command 'git.exe' @('pull','--ff-only','origin','main')
    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    if ($result.Code -eq 0) {
        $updateStatus.Text = 'App: update complete'
        $updateStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        return $true
    }

    $updateStatus.Text = 'App: update failed'
    $updateStatus.ForeColor = [System.Drawing.Color]::IndianRed
    return $false
}

function Connect-Watch {
    $adb = Find-Adb
    if (-not $adb) {
        [System.Windows.Forms.MessageBox]::Show('adb.exe was not found. Install Android Platform-Tools first.','ADB not found') | Out-Null
        return
    }

    $endpoint = [Microsoft.VisualBasic.Interaction]::InputBox(
        'Enter the IP:Port shown on the watch Wireless debugging screen. Example: 192.168.213.79:35345',
        'Connect Xiaomi Watch 5',
        ''
    ).Trim()

    if (-not $endpoint) { return }

    if ($endpoint -notmatch '^\d{1,3}(?:\.\d{1,3}){3}:\d+$') {
        [System.Windows.Forms.MessageBox]::Show('Invalid format. Enter IP:Port.','Invalid format') | Out-Null
        return
    }

    Write-Log ('Connecting to ' + $endpoint + ' ...')
    $result = Run-Command $adb @('connect',$endpoint)
    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    Start-Sleep -Milliseconds 500
    [void](Get-WatchStatus)
}

function Install-WatchFace {
    if (-not (Get-WatchStatus)) {
        [System.Windows.Forms.MessageBox]::Show(
            'The watch is not connected. Enable Wireless debugging, then click Connect Watch.',
            'Watch not connected'
        ) | Out-Null
        return
    }

    $installer = Join-Path $repoRoot 'install-watch.ps1'
    if (-not (Test-Path $installer)) {
        Write-Log 'install-watch.ps1 was not found.'
        return
    }

    Write-Log 'Building and installing the watch face...'
    $powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $result = Run-Command $powershell @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',$installer)

    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    if ($result.Code -eq 0) {
        Write-Log 'Watch face install complete.'
        [System.Windows.Forms.MessageBox]::Show('Neon Core Blue was installed on Xiaomi Watch 5.','Done') | Out-Null
    }
    else {
        Write-Log ('Install failed. Exit code: ' + $result.Code)
    }
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Xiaomi Watch 5 NeonFace Manager'
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(720,550)
$form.MinimumSize = New-Object System.Drawing.Size(736,589)
$form.Font = New-Object System.Drawing.Font('Segoe UI',10)
$form.BackColor = [System.Drawing.Color]::FromArgb(19,22,27)
$form.ForeColor = [System.Drawing.Color]::White

$title = New-Object System.Windows.Forms.Label
$title.Text = 'Xiaomi Watch 5 - Neon Core Blue'
$title.Font = New-Object System.Drawing.Font('Segoe UI',18,[System.Drawing.FontStyle]::Bold)
$title.AutoSize = $true
$title.Location = New-Object System.Drawing.Point(28,22)
$form.Controls.Add($title)

$updateStatus = New-Object System.Windows.Forms.Label
$updateStatus.Text = 'App: update not checked'
$updateStatus.AutoSize = $true
$updateStatus.Location = New-Object System.Drawing.Point(31,79)
$form.Controls.Add($updateStatus)

$watchStatus = New-Object System.Windows.Forms.Label
$watchStatus.Text = 'Watch: checking...'
$watchStatus.AutoSize = $true
$watchStatus.Location = New-Object System.Drawing.Point(31,108)
$form.Controls.Add($watchStatus)

function New-AppButton([string]$text,[int]$x,[int]$y,[int]$width) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $text
    $button.Location = New-Object System.Drawing.Point($x,$y)
    $button.Size = New-Object System.Drawing.Size($width,44)
    $button.FlatStyle = 'Flat'
    $button.BackColor = [System.Drawing.Color]::FromArgb(35,41,50)
    $button.ForeColor = [System.Drawing.Color]::White
    return $button
}

$btnCheck = New-AppButton 'Check Update' 31 150 150
$btnUpdate = New-AppButton 'Update App' 194 150 150
$btnConnect = New-AppButton 'Connect Watch' 357 150 150
$btnInstall = New-AppButton 'Install Watch Face' 520 150 165
$btnAll = New-AppButton 'Update + Install' 31 206 654
$btnAll.BackColor = [System.Drawing.Color]::FromArgb(0,101,145)
$btnAll.Font = New-Object System.Drawing.Font('Segoe UI',11,[System.Drawing.FontStyle]::Bold)

$form.Controls.AddRange(@($btnCheck,$btnUpdate,$btnConnect,$btnInstall,$btnAll))

$logBox = New-Object System.Windows.Forms.TextBox
$logBox.Location = New-Object System.Drawing.Point(31,285)
$logBox.Size = New-Object System.Drawing.Size(654,225)
$logBox.Multiline = $true
$logBox.ReadOnly = $true
$logBox.ScrollBars = 'Vertical'
$logBox.BackColor = [System.Drawing.Color]::FromArgb(10,12,15)
$logBox.ForeColor = [System.Drawing.Color]::Gainsboro
$logBox.Font = New-Object System.Drawing.Font('Consolas',9)
$form.Controls.Add($logBox)

$btnCheck.Add_Click({ Check-Update })
$btnUpdate.Add_Click({ [void](Update-App) })
$btnConnect.Add_Click({ Connect-Watch })
$btnInstall.Add_Click({ Install-WatchFace })
$btnAll.Add_Click({ [void](Update-App); Install-WatchFace })

$form.Add_Shown({
    Write-Log 'Manager started.'
    [void](Get-WatchStatus)
})

[void]$form.ShowDialog()
