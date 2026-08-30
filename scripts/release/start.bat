@echo off
call "%~dp0scripts\jdk\download.bat"
if errorlevel 1 exit /b %ERRORLEVEL%
"%~dp0jdk\bin\java.exe" -jar "%~dp0syncnuke-desktop.jar" %*
pause
