@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "TARGET=%~dp0START_MANAGER.cmd"
set "WORKDIR=%~dp0"
set "SHORTCUT=%USERPROFILE%\Desktop\Xiaomi Watch 5 NeonFace.lnk"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell; " ^
  "$sc = $ws.CreateShortcut('%SHORTCUT%'); " ^
  "$sc.TargetPath = '%TARGET%'; " ^
  "$sc.WorkingDirectory = '%WORKDIR%'; " ^
  "$sc.IconLocation = 'shell32.dll,168'; " ^
  "$sc.Description = 'Xiaomi Watch 5 NeonFace Manager'; " ^
  "$sc.Save()"

if errorlevel 1 (
  echo Failed to create desktop shortcut.
  pause
  exit /b 1
)

echo Desktop shortcut created:
echo %SHORTCUT%
echo.
pause
exit /b 0
