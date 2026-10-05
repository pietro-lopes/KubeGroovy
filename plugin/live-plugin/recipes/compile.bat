@echo off
setlocal
rem Compile plugin.groovy with the IDE's own Groovy compiler.
rem Override defaults via env vars: INTELLIJ_HOME, LIVEPLUGIN_LIB.
rem Usage: compile.bat  (double-click or run from cmd; exit code 0 = OK)

if not defined INTELLIJ_HOME set "INTELLIJ_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4"
if not defined LIVEPLUGIN_LIB set "LIVEPLUGIN_LIB=%APPDATA%\JetBrains\IntelliJIdea2025.3\plugins\LivePlugin\lib"

set "OUT=%~dp0out"
if not exist "%OUT%" mkdir "%OUT%"

set "CP=%INTELLIJ_HOME%\lib\*;%INTELLIJ_HOME%\lib\modules\*;%LIVEPLUGIN_LIB%\*;%INTELLIJ_HOME%\plugins\Groovy\lib\Groovy.jar;%INTELLIJ_HOME%\plugins\java\lib\*"

java -cp "%INTELLIJ_HOME%\lib\groovy.jar" org.codehaus.groovy.tools.FileSystemCompiler -cp "%CP%" -d "%OUT%" "%~dp0plugin.groovy"
if errorlevel 1 (
  echo COMPILE FAILED
  exit /b 1
) else (
  echo COMPILE OK, classes in %OUT%
)
