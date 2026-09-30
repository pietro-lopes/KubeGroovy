@echo off
setlocal
rem Force-copy this plugin script into the LivePlugin folder. JSON stays in the project.
rem Usage: install.bat  (exit code 0 = OK)

set "DEST=C:\Users\Pietro\AppData\Roaming\JetBrains\IntelliJIdea2025.3\live-plugins\events"
if not exist "%DEST%" mkdir "%DEST%"

rem NOTE: %~dp0 ends with a backslash, so "%~dp0" would parse as an escaped
rem quote. Strip it first.
set "SRC=%~dp0"
set "SRC=%SRC:~0,-1%"

robocopy "%SRC%" "%DEST%" /E /XD out /XF *.bat *.json /NFL /NDL /NJH /NJS
if errorlevel 8 (
  echo INSTALL FAILED
  exit /b 1
) else (
  echo INSTALL OK to %DEST%
)
