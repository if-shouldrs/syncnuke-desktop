@echo off
setlocal

for /f "tokens=3" %%V in ('findstr /B /C:"version = " "%~dp0..\..\build.gradle"') do set "VERSION=%%V"
set "VERSION=%VERSION:'=%"
set "JAR_PATH=%~dp0..\..\build\libs\syncnuke-desktop-%VERSION%-all.jar"

java -jar "%JAR_PATH%" ^
  --player mpv ^
  --player-host "\\.\pipe\mpvsocket" ^
  --launch-player ^
  %*

exit /b %ERRORLEVEL%
