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

call :ensure_mpv
if errorlevel 1 exit /b 1

for /f "tokens=3" %%V in ('findstr /B /C:"version = " "%PROJECT_DIR%\build.gradle"') do set "VERSION=%%V"
set "VERSION=%VERSION:'=%"
set "JAR_PATH=%PROJECT_DIR%\build\libs\syncnuke-desktop-%VERSION%-all.jar"

"%JAVA_COMMAND%" -jar "%JAR_PATH%" ^
  --player mpv ^
  --player-host "\\.\pipe\mpvsocket" ^
  --launch-player ^
  %*

set "EXIT_CODE=%ERRORLEVEL%"
pause
exit /b %EXIT_CODE%

:ensure_mpv
where mpv.exe >nul 2>&1
if not errorlevel 1 exit /b 0

set "MPV_PATH_FILE=%PROJECT_DIR%\dist\mpv\path"
set "MPV_COMMAND="
if exist "%MPV_PATH_FILE%" set /p "MPV_COMMAND="<"%MPV_PATH_FILE%"
if defined MPV_COMMAND if exist "%MPV_COMMAND%" goto configure_mpv

set /p "MPV_COMMAND=MPV was not found on PATH. Enter the path to mpv.exe: "
if not defined MPV_COMMAND (
  echo An MPV executable path is required.
  exit /b 1
)
set "MPV_COMMAND=%MPV_COMMAND:"=%"
if not exist "%MPV_COMMAND%" (
  echo MPV executable not found: %MPV_COMMAND%
  exit /b 1
)

for %%I in ("%MPV_COMMAND%") do set "MPV_COMMAND=%%~fI"
for %%I in ("%MPV_PATH_FILE%") do if not exist "%%~dpI" mkdir "%%~dpI"
>"%MPV_PATH_FILE%" echo %MPV_COMMAND%

:configure_mpv
for %%I in ("%MPV_COMMAND%") do set "PATH=%%~dpI;%PATH%"
exit /b 0
