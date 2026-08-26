@echo off
setlocal

echo Select a video player:
echo 1^) MPV
set /p "PLAYER=Selection: "

if "%PLAYER%"=="1" goto mpv
echo Invalid selection: %PLAYER%
pause
exit /b 1

:mpv
call "%~dp0scripts\mpv\start.bat" %*
exit /b %ERRORLEVEL%
