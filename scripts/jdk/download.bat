@echo off
setlocal

set "PROJECT_DIR=%~dp0..\.."
set "JDK_DIR=%PROJECT_DIR%\dist\jdk"

if exist "%JDK_DIR%\bin\java.exe" exit /b 0

set "JDK_URL=https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
set "JDK_ARCHIVE=%TEMP%\syncnuke-jdk-%RANDOM%.zip"
set "JDK_EXTRACT=%TEMP%\syncnuke-jdk-%RANDOM%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "try {" ^
  "Invoke-WebRequest -Uri $env:JDK_URL -OutFile $env:JDK_ARCHIVE;" ^
  "Expand-Archive -Path $env:JDK_ARCHIVE -DestinationPath $env:JDK_EXTRACT;" ^
  "$jdk = Get-ChildItem $env:JDK_EXTRACT -Directory | Select-Object -First 1;" ^
  "New-Item -ItemType Directory -Force -Path $env:JDK_DIR | Out-Null;" ^
  "Copy-Item -Path (Join-Path $jdk.FullName '*') -Destination $env:JDK_DIR -Recurse -Force;" ^
  "} finally {" ^
  "Remove-Item $env:JDK_ARCHIVE -Force -ErrorAction SilentlyContinue;" ^
  "Remove-Item $env:JDK_EXTRACT -Recurse -Force -ErrorAction SilentlyContinue;" ^
  "}"
if errorlevel 1 exit /b 1

if not exist "%JDK_DIR%\bin\java.exe" exit /b 1
exit /b 0
