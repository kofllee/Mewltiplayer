@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\helper.ps1" %*
exit /b %errorlevel%
