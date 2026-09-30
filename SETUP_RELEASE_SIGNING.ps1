$ErrorActionPreference = 'Stop'

$repo = 'wer930821/XiaomiWatch5-NeonFace'
$keyAlias = 'neonface'
$releaseDir = Join-Path $PSScriptRoot 'release'
$keystore = Join-Path $releaseDir 'neonface-release.jks'

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

function New-RandomPassword {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).Replace('/','A').Replace('+','B').Replace('=','')
}

$storePass = New-RandomPassword
$keyPass = $storePass

$keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
if (-not $keytool -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (Test-Path $candidate) { $keytool = Get-Item $candidate }
}
if (-not $keytool) { throw 'keytool.exe was not found. Install JDK 17 first.' }

if (Test-Path $keystore) {
    Write-Host 'Release keystore already exists. Nothing was overwritten.' -ForegroundColor Yellow
    Write-Host $keystore
    exit 0
}

$keytoolArgs = @(
    '-genkeypair', '-v',
    '-keystore', $keystore,
    '-storepass', $storePass,
    '-keypass', $keyPass,
    '-alias', $keyAlias,
    '-keyalg', 'RSA',
    '-keysize', '2048',
    '-validity', '10000',
    '-dname', 'CN=NeonFace, OU=Personal, O=NeonFace, L=Taichung, ST=Taiwan, C=TW'
)
& $keytool.Source @keytoolArgs
if ($LASTEXITCODE -ne 0) { throw 'Failed to create release keystore.' }

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
$backup = Join-Path $releaseDir 'KEEP_PRIVATE_signing_backup.txt'
@(
    ('RELEASE_STORE_PASSWORD=' + $storePass),
    ('RELEASE_KEY_ALIAS=' + $keyAlias),
    ('RELEASE_KEY_PASSWORD=' + $keyPass),
    '',
    'KEEP THIS FILE PRIVATE. DO NOT COMMIT OR SHARE IT.',
    'Keep the JKS file and these passwords. They are required for future app updates.'
) | Set-Content -Encoding ASCII $backup

Write-Host ''
Write-Host 'Release keystore created successfully.' -ForegroundColor Green
Write-Host ('Keystore: ' + $keystore)
Write-Host ('Backup: ' + $backup)
Write-Host ''

$gh = Get-Command gh.exe -ErrorAction SilentlyContinue
if ($gh) {
    & $gh.Source auth status
    if ($LASTEXITCODE -eq 0) {
        $base64 | & $gh.Source secret set RELEASE_KEYSTORE_BASE64 -R $repo
        $storePass | & $gh.Source secret set RELEASE_STORE_PASSWORD -R $repo
        $keyAlias | & $gh.Source secret set RELEASE_KEY_ALIAS -R $repo
        $keyPass | & $gh.Source secret set RELEASE_KEY_PASSWORD -R $repo
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'GitHub Actions secrets configured successfully.' -ForegroundColor Green
            exit 0
        }
    }
}

Write-Host 'GitHub CLI is unavailable or not authenticated.' -ForegroundColor Yellow
Write-Host 'Add these four GitHub Actions secrets manually:'
Write-Host ''
Write-Host 'RELEASE_KEYSTORE_BASE64'
Write-Host $base64
Write-Host ''
Write-Host 'RELEASE_STORE_PASSWORD'
Write-Host $storePass
Write-Host ''
Write-Host 'RELEASE_KEY_ALIAS'
Write-Host $keyAlias
Write-Host ''
Write-Host 'RELEASE_KEY_PASSWORD'
Write-Host $keyPass
