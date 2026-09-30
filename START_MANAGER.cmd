@echo off
setlocal
cd /d "%~dp0"
title Xiaomi Watch 5 NeonFace Manager
set "PSEXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

"%PSEXE%" -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0WatchFaceManager.ps1"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Manager failed to start. Exit code: %ERR%
  echo.
  pause
)
exit /b %ERR%
