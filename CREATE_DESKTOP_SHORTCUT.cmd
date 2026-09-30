@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "VBS=%~dp0START_MANAGER.vbs"
set "SHORTCUT=%USERPROFILE%\Desktop\Xiaomi Watch 5 NeonFace.lnk"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command ^
  "$ws = New-Object -ComObject WScript.Shell; " ^
  "$sc = $ws.CreateShortcut('%SHORTCUT%'); " ^
  "$sc.TargetPath = 'wscript.exe'; " ^
  "$sc.Arguments = '""%VBS%""'; " ^
  "$sc.WorkingDirectory = '%~dp0'; " ^
  "$sc.IconLocation = 'shell32.dll,168'; " ^
  "$sc.Description = 'Xiaomi Watch 5 NeonFace Manager'; " ^
  "$sc.Save()"

exit /b %ERRORLEVEL%
