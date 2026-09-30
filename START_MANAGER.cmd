@echo off
setlocal
cd /d "%~dp0"
title Xiaomi Watch 5 NeonFace Manager
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0WatchFaceManager.ps1"
exit /b %ERRORLEVEL%
