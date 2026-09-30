$ErrorActionPreference = 'Stop'

$repo = 'wer930821/XiaomiWatch5-NeonFace'
$alias = 'neonface'
$releaseDir = Join-Path $PSScriptRoot 'release'
$keystore = Join-Path $releaseDir 'neonface-release.jks'

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

function New-Password {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes).Replace('/','A').Replace('+','B').Replace('=','')
}

$storePass = New-Password
$keyPass = $storePass

$keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
if (-not $keytool) {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
        if (Test-Path $candidate) { $keytool = Get-Item $candidate }
    }
}
if (-not $keytool) { throw '找不到 keytool.exe。請先安裝 JDK 17。' }

if (Test-Path $keystore) {
    Write-Host 'release keystore 已存在，不會覆蓋。' -ForegroundColor Yellow
    Write-Host $keystore
    exit 0
}

$args = @(
    '-genkeypair',
    '-v',
    '-keystore', $keystore,
    '-storepass', $storePass,
    '-keypass', $keyPass,
    '-alias', $alias,
    '-keyalg', 'RSA',
    '-keysize', '2048',
    '-validity', '10000',
    '-dname', 'CN=NeonFace, OU=Personal, O=NeonFace, L=Taichung, ST=Taiwan, C=TW'
)
& $keytool.Source @args
if ($LASTEXITCODE -ne 0) { throw '建立簽章失敗。' }

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
$backup = Join-Path $releaseDir 'KEEP_PRIVATE_signing_backup.txt'
$backupLines = @(
    ('RELEASE_STORE_PASSWORD=' + $storePass),
    ('RELEASE_KEY_ALIAS=' + $alias),
    ('RELEASE_KEY_PASSWORD=' + $keyPass),
    '',
    '這是更新簽章的備份資料。請勿上傳 GitHub、不要公開分享。',
    '若遺失 keystore 或密碼，以後將無法用相同簽章覆蓋更新已安裝版本。'
)
$backupLines | Set-Content -Encoding UTF8 $backup

Write-Host ''
Write-Host '固定簽章已建立。' -ForegroundColor Green
Write-Host ('Keystore: ' + $keystore)
Write-Host ('備份: ' + $backup)
Write-Host ''

$gh = Get-Command gh.exe -ErrorAction SilentlyContinue
if ($gh) {
    Write-Host '偵測到 GitHub CLI，準備設定 repository secrets。'
    & $gh.Source auth status
    if ($LASTEXITCODE -eq 0) {
        $base64 | & $gh.Source secret set RELEASE_KEYSTORE_BASE64 -R $repo
        $storePass | & $gh.Source secret set RELEASE_STORE_PASSWORD -R $repo
        $alias | & $gh.Source secret set RELEASE_KEY_ALIAS -R $repo
        $keyPass | & $gh.Source secret set RELEASE_KEY_PASSWORD -R $repo
        if ($LASTEXITCODE -eq 0) {
            Write-Host ''
            Write-Host 'GitHub Actions 簽章 secrets 已設定完成。' -ForegroundColor Green
            Write-Host '接下來到 GitHub Actions 執行 Build latest APKs 即可。'
            exit 0
        }
    }
}

Write-Host ''
Write-Host '尚未自動寫入 GitHub Secrets。' -ForegroundColor Yellow
Write-Host '請到 GitHub repository -> Settings -> Secrets and variables -> Actions'
Write-Host '新增以下 4 個 secrets：'
Write-Host ''
Write-Host 'RELEASE_KEYSTORE_BASE64'
Write-Host $base64
Write-Host ''
Write-Host 'RELEASE_STORE_PASSWORD'
Write-Host $storePass
Write-Host ''
Write-Host 'RELEASE_KEY_ALIAS'
Write-Host $alias
Write-Host ''
Write-Host 'RELEASE_KEY_PASSWORD'
Write-Host $keyPass
