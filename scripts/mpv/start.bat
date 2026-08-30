@echo off
setlocal

set "PROJECT_DIR=%~dp0..\.."
set "JAVA_COMMAND=java"

if exist "%PROJECT_DIR%\VERSION" goto release_layout
for /f "tokens=3" %%V in ('findstr /B /C:"version = " "%PROJECT_DIR%\build.gradle"') do set "VERSION=%%V"
set "VERSION=%VERSION:'=%"
set "JAR_PATH=%PROJECT_DIR%\build\libs\syncnuke-desktop-%VERSION%-all.jar"
set "RUNTIME_DIR=%PROJECT_DIR%\dist"
goto layout_ready

:release_layout
set /p "VERSION="<"%PROJECT_DIR%\VERSION"
set "JAR_PATH=%PROJECT_DIR%\syncnuke-desktop-%VERSION%-all.jar"
set "RUNTIME_DIR=%PROJECT_DIR%"

:layout_ready
where java >nul 2>&1
if errorlevel 1 (
  call "%~dp0..\jdk\download.bat"
  if errorlevel 1 exit /b 1
  set "JAVA_COMMAND=%RUNTIME_DIR%\jdk\bin\java.exe"
)

call :validate_sync_options %*
if errorlevel 1 exit /b 1

call :ensure_mpv
if errorlevel 1 exit /b 1

"%JAVA_COMMAND%" "-Dsyncnuke.mpv.executable=%MPV_COMMAND%" -jar "%JAR_PATH%" ^
  --player mpv ^
  --player-host "\\.\pipe\mpvsocket" ^
  --launch-player ^
  %*

set "EXIT_CODE=%ERRORLEVEL%"
pause
exit /b %EXIT_CODE%

:validate_sync_options
set "HAS_USER="
set "HAS_ROOM="

:validate_next_option
if "%~1"=="" goto validate_options_done
if /I "%~1"=="--user" set "HAS_USER=1"
if /I "%~1"=="--room" set "HAS_ROOM=1"
shift
goto validate_next_option

:validate_options_done
if not defined HAS_USER goto missing_sync_options
if not defined HAS_ROOM goto missing_sync_options
exit /b 0

:missing_sync_options
echo Usage: %~nx0 --user ^<name^> --room ^<name^> [options]
exit /b 1

:ensure_mpv
where mpv.exe >nul 2>&1
if not errorlevel 1 (
  set "MPV_COMMAND=mpv.exe"
  exit /b 0
)

where mpvnet.exe >nul 2>&1
if not errorlevel 1 (
  set "MPV_COMMAND=mpvnet.exe"
  exit /b 0
)

set "MPV_PATH_FILE=%RUNTIME_DIR%\mpv\path"
set "MPV_COMMAND="
if exist "%MPV_PATH_FILE%" set /p "MPV_COMMAND="<"%MPV_PATH_FILE%"
if defined MPV_COMMAND if exist "%MPV_COMMAND%\mpv.exe" set "MPV_COMMAND=%MPV_COMMAND%\mpv.exe"
if defined MPV_COMMAND if exist "%MPV_COMMAND%\mpvnet.exe" set "MPV_COMMAND=%MPV_COMMAND%\mpvnet.exe"
if defined MPV_COMMAND if not exist "%MPV_COMMAND%\NUL" if exist "%MPV_COMMAND%" goto configure_mpv

set /p "MPV_COMMAND=MPV or MPV.NET was not found on PATH. Enter the path to the player executable: "
if not defined MPV_COMMAND (
  echo An MPV executable path is required.
  exit /b 1
)
set "MPV_COMMAND=%MPV_COMMAND:"=%"
if exist "%MPV_COMMAND%\mpv.exe" set "MPV_COMMAND=%MPV_COMMAND%\mpv.exe"
if exist "%MPV_COMMAND%\mpvnet.exe" set "MPV_COMMAND=%MPV_COMMAND%\mpvnet.exe"
if exist "%MPV_COMMAND%\NUL" (
  echo MPV executable not found: %MPV_COMMAND%
  exit /b 1
)
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
