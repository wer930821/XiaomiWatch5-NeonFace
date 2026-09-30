@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.13"
set "GRADLE_HOME=%USERPROFILE%\.gradle\manual-dists\gradle-%GRADLE_VERSION%"
set "GRADLE_BAT=%GRADLE_HOME%\bin\gradle.bat"

if exist "%GRADLE_BAT%" goto run

echo [1/3] Gradle %GRADLE_VERSION% not found. Downloading...
set "ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
if errorlevel 1 exit /b 1

echo [2/3] Extracting Gradle...
if not exist "%USERPROFILE%\.gradle\manual-dists" mkdir "%USERPROFILE%\.gradle\manual-dists"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; if (Test-Path '%GRADLE_HOME%') { Remove-Item -Recurse -Force '%GRADLE_HOME%' }; Expand-Archive -Path '%ZIP%' -DestinationPath '%USERPROFILE%\.gradle\manual-dists' -Force"
if errorlevel 1 exit /b 1

del /q "%ZIP%" >nul 2>&1

echo [3/3] Gradle ready.

:run
call "%GRADLE_BAT%" %*
exit /b %ERRORLEVEL%
