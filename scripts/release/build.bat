@echo off
setlocal

set "PROJECT_DIR=%~dp0..\.."
set "SYNCNUKE_NO_PAUSE=1"
call "%~dp0..\build\build.bat"
set "EXIT_CODE=%ERRORLEVEL%"
set "SYNCNUKE_NO_PAUSE="
if not "%EXIT_CODE%"=="0" goto finish

for /f "tokens=3" %%V in ('findstr /B /C:"version = " "%PROJECT_DIR%\build.gradle"') do set "VERSION=%%V"
set "VERSION=%VERSION:'=%"
set "RELEASE_DIR=%PROJECT_DIR%\dist\syncnuke-desktop-%VERSION%"
set "JAR=%PROJECT_DIR%\build\libs\syncnuke-desktop-%VERSION%-all.jar"

if not exist "%RELEASE_DIR%\scripts\mpv" mkdir "%RELEASE_DIR%\scripts\mpv"
if not exist "%RELEASE_DIR%\scripts\jdk" mkdir "%RELEASE_DIR%\scripts\jdk"
copy /Y "%JAR%" "%RELEASE_DIR%\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\mpv\start.sh" "%RELEASE_DIR%\scripts\mpv\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\mpv\start.bat" "%RELEASE_DIR%\scripts\mpv\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\jdk\download.sh" "%RELEASE_DIR%\scripts\jdk\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\jdk\download.bat" "%RELEASE_DIR%\scripts\jdk\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\start\start.sh" "%RELEASE_DIR%\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\scripts\start\start.bat" "%RELEASE_DIR%\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\README.md" "%RELEASE_DIR%\" >nul || goto copy_failed
copy /Y "%PROJECT_DIR%\LICENSE.md" "%RELEASE_DIR%\" >nul || goto copy_failed
>"%RELEASE_DIR%\VERSION" echo %VERSION%

echo Release created at %RELEASE_DIR%
goto finish

:copy_failed
set "EXIT_CODE=1"

:finish
pause
exit /b %EXIT_CODE%
