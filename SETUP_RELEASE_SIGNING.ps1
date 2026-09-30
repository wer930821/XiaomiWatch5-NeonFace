$ErrorActionPreference = 'Stop'

$releaseDir = Join-Path $PSScriptRoot 'release'
$keystore = Join-Path $releaseDir 'neonface-release.jks'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

$keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
if (-not $keytool -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (Test-Path $candidate) { $keytool = Get-Item $candidate }
}
if (-not $keytool) { throw 'keytool.exe was not found. Install JDK 17 first.' }

if (Test-Path $keystore) {
    Write-Host 'Release keystore already exists. It will not be overwritten.' -ForegroundColor Yellow
    Write-Host $keystore
    exit 0
}

Write-Host 'Creating the fixed NeonFace release signing key.'
Write-Host 'keytool will ask you to create a password. Save that password somewhere private.'
Write-Host ''

& $keytool.Source -genkeypair -v -keystore $keystore -alias neonface -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=NeonFace, OU=Personal, O=NeonFace, L=Taichung, ST=Taiwan, C=TW'
if ($LASTEXITCODE -ne 0) { throw 'Failed to create release keystore.' }

Write-Host ''
Write-Host 'Release signing key created successfully.' -ForegroundColor Green
Write-Host ('Keystore: ' + $keystore)
Write-Host 'Keep the release folder private and backed up.'
