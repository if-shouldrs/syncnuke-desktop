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

"%JAVA_COMMAND%" ^
  -classpath "%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain ^
  --project-dir "%PROJECT_DIR%" ^
  build

set "EXIT_CODE=%ERRORLEVEL%"
if not defined SYNCNUKE_NO_PAUSE pause
exit /b %EXIT_CODE%
