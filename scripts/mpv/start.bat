@echo off
setlocal

set "PROJECT_DIR=%~dp0..\.."
set "JAVA_COMMAND=java"

where java >nul 2>&1
if errorlevel 1 (
  call "%~dp0..\jdk\download.bat"
  if errorlevel 1 exit /b 1
  set "JAVA_COMMAND=%PROJECT_DIR%\dist\jdk\bin\java.exe"
)

for /f "tokens=3" %%V in ('findstr /B /C:"version = " "%PROJECT_DIR%\build.gradle"') do set "VERSION=%%V"
set "VERSION=%VERSION:'=%"
set "JAR_PATH=%PROJECT_DIR%\build\libs\syncnuke-desktop-%VERSION%-all.jar"

"%JAVA_COMMAND%" -jar "%JAR_PATH%" ^
  --player mpv ^
  --player-host "\\.\pipe\mpvsocket" ^
  --launch-player ^
  %*

exit /b %ERRORLEVEL%
