Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName Microsoft.VisualBasic

[System.Windows.Forms.Application]::EnableVisualStyles()
$repoRoot = $PSScriptRoot

function Find-Adb {
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $sdk) { return $sdk }
    return $null
}

function Log([string]$msg) {
    if ([string]::IsNullOrWhiteSpace($msg)) { return }
    $logBox.AppendText(('[' + (Get-Date -Format 'HH:mm:ss') + '] ' + $msg + [Environment]::NewLine))
    $logBox.SelectionStart = $logBox.TextLength
    $logBox.ScrollToCaret()
    [System.Windows.Forms.Application]::DoEvents()
}

function Run([string]$file,[string[]]$args) {
    try {
        Push-Location $repoRoot
        $lines = & $file @args 2>&1
        $code = $LASTEXITCODE
        $text = ($lines | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
        [pscustomobject]@{Code=$code;Out=$text;Err=''}
    }
    catch {
        [pscustomobject]@{Code=1;Out='';Err=$_.Exception.Message}
    }
    finally {
        Pop-Location
    }
}

function Watch-Status {
    $adb = Find-Adb
    if (-not $adb) {
        $watchStatus.Text = '手錶：找不到 ADB'
        $watchStatus.ForeColor = [Drawing.Color]::IndianRed
        return $false
    }
    $r = Run $adb @('devices')
    $dev = $null
    foreach ($line in ($r.Out -split [Environment]::NewLine)) {
        if ($line -match '^(.+?)\s+device$') { $dev=$Matches[1].Trim(); break }
    }
    if ($dev) {
        $watchStatus.Text = '手錶：已連線  ' + $dev
        $watchStatus.ForeColor = [Drawing.Color]::MediumSeaGreen
        return $true
    }
    $watchStatus.Text = '手錶：尚未連線'
    $watchStatus.ForeColor = [Drawing.Color]::OrangeRed
    return $false
}

function Check-Update {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { Log '找不到 Git。'; return }
    Log '正在檢查 GitHub 更新...'
    $f = Run 'git.exe' @('fetch','origin','main')
    if ($f.Code -ne 0) { Log ('檢查失敗：' + $f.Err.Trim()); return }
    $l = Run 'git.exe' @('rev-parse','HEAD')
    $r = Run 'git.exe' @('rev-parse','origin/main')
    if ($l.Out.Trim() -eq $r.Out.Trim()) {
        $updateStatus.Text='程式：已是最新版'
        $updateStatus.ForeColor=[Drawing.Color]::MediumSeaGreen
        Log '目前已是最新版。'
    } else {
        $updateStatus.Text='程式：有新版本可更新'
        $updateStatus.ForeColor=[Drawing.Color]::DeepSkyBlue
        Log '發現新版本。'
    }
}

function Update-App {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { Log '找不到 Git。'; return $false }
    Log '正在從 GitHub 更新...'
    $r = Run 'git.exe' @('pull','--ff-only','origin','main')
    if ($r.Out.Trim()) { Log $r.Out.Trim() }
    if ($r.Err.Trim()) { Log $r.Err.Trim() }
    if ($r.Code -eq 0) {
        $updateStatus.Text='程式：更新完成'
        $updateStatus.ForeColor=[Drawing.Color]::MediumSeaGreen
        return $true
    }
    $updateStatus.Text='程式：更新失敗'
    $updateStatus.ForeColor=[Drawing.Color]::IndianRed
    return $false
}

function Connect-Watch {
    $adb=Find-Adb
    if (-not $adb) { [Windows.Forms.MessageBox]::Show('找不到 adb.exe。')|Out-Null; return }
    $ep=[Microsoft.VisualBasic.Interaction]::InputBox('輸入手錶「無線偵錯」首頁顯示的 IP:Port，例如 192.168.213.79:35345','連線 Xiaomi Watch 5','').Trim()
    if (-not $ep) { return }
    if ($ep -notmatch '^\d{1,3}(?:\.\d{1,3}){3}:\d+$') { [Windows.Forms.MessageBox]::Show('格式錯誤，請輸入 IP:Port。')|Out-Null; return }
    Log ('正在連線 ' + $ep)
    $r=Run $adb @('connect',$ep)
    if ($r.Out.Trim()) { Log $r.Out.Trim() }
    if ($r.Err.Trim()) { Log $r.Err.Trim() }
    [void](Watch-Status)
}

function Install-Face {
    if (-not (Watch-Status)) {
        [Windows.Forms.MessageBox]::Show('目前沒有連線到手錶。請先開啟無線偵錯，再按「連線手錶」。','手錶未連線')|Out-Null
        return
    }
    Log '開始編譯並安裝錶盤...'
    $ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $r=Run $ps @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $repoRoot 'install-watch.ps1'))
    if ($r.Out.Trim()) { Log $r.Out.Trim() }
    if ($r.Err.Trim()) { Log $r.Err.Trim() }
    if ($r.Code -eq 0) {
        Log '錶盤安裝完成。'
        [Windows.Forms.MessageBox]::Show('Neon Core Blue 已更新到 Xiaomi Watch 5。','完成')|Out-Null
    } else {
        Log ('安裝失敗，Exit code: ' + $r.Code)
    }
}

$form=New-Object Windows.Forms.Form
$form.Text='Xiaomi Watch 5 NeonFace 管理程式'
$form.StartPosition='CenterScreen'
$form.ClientSize=New-Object Drawing.Size(720,550)
$form.Font=New-Object Drawing.Font('Microsoft JhengHei UI',10)
$form.BackColor=[Drawing.Color]::FromArgb(19,22,27)
$form.ForeColor=[Drawing.Color]::White

$title=New-Object Windows.Forms.Label
$title.Text='Xiaomi Watch 5  Neon Core Blue'
$title.Font=New-Object Drawing.Font('Microsoft JhengHei UI',18,[Drawing.FontStyle]::Bold)
$title.AutoSize=$true
$title.Location=New-Object Drawing.Point(28,22)
$form.Controls.Add($title)

$updateStatus=New-Object Windows.Forms.Label
$updateStatus.Text='程式：尚未檢查更新'
$updateStatus.AutoSize=$true
$updateStatus.Location=New-Object Drawing.Point(31,79)
$form.Controls.Add($updateStatus)

$watchStatus=New-Object Windows.Forms.Label
$watchStatus.Text='手錶：檢查中...'
$watchStatus.AutoSize=$true
$watchStatus.Location=New-Object Drawing.Point(31,108)
$form.Controls.Add($watchStatus)

function Btn([string]$txt,[int]$x,[int]$y,[int]$w) {
    $b=New-Object Windows.Forms.Button
    $b.Text=$txt
    $b.Location=New-Object Drawing.Point($x,$y)
    $b.Size=New-Object Drawing.Size($w,44)
    $b.FlatStyle='Flat'
    $b.BackColor=[Drawing.Color]::FromArgb(35,41,50)
    $b.ForeColor=[Drawing.Color]::White
    $b
}

$btnCheck=Btn '檢查更新' 31 150 150
$btnUpdate=Btn '更新程式' 194 150 150
$btnConnect=Btn '連線手錶' 357 150 150
$btnInstall=Btn '安裝／更新錶盤' 520 150 165
$btnAll=Btn '一鍵更新＋安裝' 31 206 654
$btnAll.BackColor=[Drawing.Color]::FromArgb(0,101,145)
$btnAll.Font=New-Object Drawing.Font('Microsoft JhengHei UI',11,[Drawing.FontStyle]::Bold)
$form.Controls.AddRange(@($btnCheck,$btnUpdate,$btnConnect,$btnInstall,$btnAll))

$logBox=New-Object Windows.Forms.TextBox
$logBox.Location=New-Object Drawing.Point(31,285)
$logBox.Size=New-Object Drawing.Size(654,225)
$logBox.Multiline=$true
$logBox.ReadOnly=$true
$logBox.ScrollBars='Vertical'
$logBox.BackColor=[Drawing.Color]::FromArgb(10,12,15)
$logBox.ForeColor=[Drawing.Color]::Gainsboro
$logBox.Font=New-Object Drawing.Font('Consolas',9)
$form.Controls.Add($logBox)

$btnCheck.Add_Click({Check-Update})
$btnUpdate.Add_Click({[void](Update-App)})
$btnConnect.Add_Click({Connect-Watch})
$btnInstall.Add_Click({Install-Face})
$btnAll.Add_Click({[void](Update-App); Install-Face})
$form.Add_Shown({Log '管理程式已啟動。'; [void](Watch-Status)})

[void]$form.ShowDialog()
