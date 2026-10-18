@echo off
setlocal
set "ROOT=%~dp0.."
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "GRADLE_USER_HOME=%ROOT%\compile\.gradle-home"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ui-debug.ps1" -Build %*
exit /b %ERRORLEVEL%
