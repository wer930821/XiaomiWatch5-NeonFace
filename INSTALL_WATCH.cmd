@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Xiaomi Watch 5 Watch Face Installer v6

echo ========================================
echo Xiaomi Watch 5 Watch Face Installer v6
echo Auto reconnect after Gradle build
echo ========================================
echo.

set "PSEXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PSEXE%" (
  echo ERROR: Windows PowerShell was not found.
  echo.
  pause
  exit /b 1
)

"%PSEXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-watch.ps1"
set "ERR=%ERRORLEVEL%"

echo.
if not "%ERR%"=="0" (
  echo INSTALL FAILED. Exit code: %ERR%
  echo Please send the last lines shown above or the install-log.txt file.
) else (
  echo INSTALL FINISHED.
)
echo.
pause
exit /b %ERR%
