Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName Microsoft.VisualBasic

[System.Windows.Forms.Application]::EnableVisualStyles()
$repoRoot = $PSScriptRoot

function T([string]$b64) {
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
}


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
        $watchStatus.Text = (T '5omL6Yy277ya5om+5LiN5YiwIEFEQg==')
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
        $watchStatus.Text = (T '5omL6Yy277ya5bey6YCj57eaICA=') + $device
        $watchStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        return $true
    }

    $watchStatus.Text = (T '5omL6Yy277ya5bCa5pyq6YCj57ea')
    $watchStatus.ForeColor = [System.Drawing.Color]::OrangeRed
    return $false
}

function Check-Update {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        Write-Log (T '5om+5LiN5YiwIEdpdOOAgg==')
        return
    }

    Write-Log (T '5q2j5Zyo5qqi5p+lIEdpdEh1YiDmm7TmlrAuLi4=')
    $fetch = Run-Command 'git.exe' @('fetch','origin','main')
    if ($fetch.Code -ne 0) {
        Write-Log ((T '5qqi5p+l5pu05paw5aSx5pWX77ya') + $fetch.Err + ' ' + $fetch.Out)
        return
    }

    $local = Run-Command 'git.exe' @('rev-parse','HEAD')
    $remote = Run-Command 'git.exe' @('rev-parse','origin/main')

    if ($local.Out.Trim() -eq $remote.Out.Trim()) {
        $updateStatus.Text = (T '56iL5byP77ya5bey5piv5pyA5paw54mI')
        $updateStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        Write-Log (T '55uu5YmN5bey5piv5pyA5paw54mI44CC')
    }
    else {
        $updateStatus.Text = (T '56iL5byP77ya5pyJ5paw54mI5pys5Y+v5pu05paw')
        $updateStatus.ForeColor = [System.Drawing.Color]::DeepSkyBlue
        Write-Log (T '55m854++5paw54mI5pys44CC')
    }
}

function Update-App {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        Write-Log (T '5om+5LiN5YiwIEdpdOOAgg==')
        return $false
    }

    Write-Log (T '5q2j5Zyo5b6eIEdpdEh1YiDmm7TmlrAuLi4=')
    $result = Run-Command 'git.exe' @('pull','--ff-only','origin','main')
    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    if ($result.Code -eq 0) {
        $updateStatus.Text = (T '56iL5byP77ya5pu05paw5a6M5oiQ')
        $updateStatus.ForeColor = [System.Drawing.Color]::MediumSeaGreen
        return $true
    }

    $updateStatus.Text = (T '56iL5byP77ya5pu05paw5aSx5pWX')
    $updateStatus.ForeColor = [System.Drawing.Color]::IndianRed
    return $false
}

function Connect-Watch {
    $adb = Find-Adb
    if (-not $adb) {
        [System.Windows.Forms.MessageBox]::Show((T '5om+5LiN5YiwIGFkYi5leGXjgILoq4vlhYjlronoo50gQW5kcm9pZCBQbGF0Zm9ybS1Ub29sc+OAgg=='),(T '5om+5LiN5YiwIEFEQg==')) | Out-Null
        return
    }

    $endpoint = [Microsoft.VisualBasic.Interaction]::InputBox(
        (T '6KuL6Ly45YWl5omL6Yy244CM54Sh57ea5YG16Yyv44CN6aaW6aCB6aGv56S655qEIElQOlBvcnTvvIzkvovlpoLvvJoxOTIuMTY4LjIxMy43OTozNTM0NQ=='),
        (T '6YCj57eaIFhpYW9taSBXYXRjaCA1'),
        ''
    ).Trim()

    if (-not $endpoint) { return }

    if ($endpoint -notmatch '^\d{1,3}(?:\.\d{1,3}){3}:\d+$') {
        [System.Windows.Forms.MessageBox]::Show((T '5qC85byP6Yyv6Kqk77yM6KuL6Ly45YWlIElQOlBvcnTjgII='),(T '5qC85byP6Yyv6Kqk')) | Out-Null
        return
    }

    Write-Log ((T '5q2j5Zyo6YCj57ea77ya') + $endpoint + ' ...')
    $result = Run-Command $adb @('connect',$endpoint)
    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    Start-Sleep -Milliseconds 500
    [void](Get-WatchStatus)
}

function Install-WatchFace {
    if (-not (Get-WatchStatus)) {
        [System.Windows.Forms.MessageBox]::Show(
            (T '55uu5YmN5rKS5pyJ6YCj57ea5Yiw5omL6Yy244CC6KuL5YWI6ZaL5ZWf54Sh57ea5YG16Yyv77yM5YaN5oyJ44CM6YCj57ea5omL6Yy244CN44CC'),
            (T '5omL6Yy25pyq6YCj57ea')
        ) | Out-Null
        return
    }

    $installer = Join-Path $repoRoot 'install-watch.ps1'
    if (-not (Test-Path $installer)) {
        Write-Log (T '5om+5LiN5YiwIGluc3RhbGwtd2F0Y2gucHMx44CC')
        return
    }

    Write-Log (T '6ZaL5aeL57eo6K2v5Lim5a6J6KOd6Yy255ukLi4u')
    $powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $result = Run-Command $powershell @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',$installer)

    if ($result.Out.Trim()) { Write-Log $result.Out.Trim() }
    if ($result.Err.Trim()) { Write-Log $result.Err.Trim() }

    if ($result.Code -eq 0) {
        Write-Log (T '6Yy255uk5a6J6KOd5a6M5oiQ44CC')
        [System.Windows.Forms.MessageBox]::Show((T 'TmVvbiBDb3JlIEJsdWUg5bey5pu05paw5YiwIFhpYW9taSBXYXRjaCA144CC'),(T '5a6M5oiQ')) | Out-Null
    }
    else {
        Write-Log ((T '5a6J6KOd5aSx5pWX77yMRXhpdCBjb2RlOiA=') + $result.Code)
    }
}

$form = New-Object System.Windows.Forms.Form
$form.Text = (T 'WGlhb21pIFdhdGNoIDUgTmVvbkZhY2Ug566h55CG56iL5byP')
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
$updateStatus.Text = (T '56iL5byP77ya5bCa5pyq5qqi5p+l5pu05paw')
$updateStatus.AutoSize = $true
$updateStatus.Location = New-Object System.Drawing.Point(31,79)
$form.Controls.Add($updateStatus)

$watchStatus = New-Object System.Windows.Forms.Label
$watchStatus.Text = (T '5omL6Yy277ya5qqi5p+l5LitLi4u')
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

$btnCheck = New-AppButton (T '5qqi5p+l5pu05paw') 31 150 150
$btnUpdate = New-AppButton (T '5pu05paw56iL5byP') 194 150 150
$btnConnect = New-AppButton (T '6YCj57ea5omL6Yy2') 357 150 150
$btnInstall = New-AppButton (T '5a6J6KOd77yP5pu05paw6Yy255uk') 520 150 165
$btnAll = New-AppButton (T '5LiA6Y215pu05paw77yL5a6J6KOd') 31 206 654
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
    Write-Log (T '566h55CG56iL5byP5bey5ZWf5YuV44CC')
    [void](Get-WatchStatus)
})

[void]$form.ShowDialog()
