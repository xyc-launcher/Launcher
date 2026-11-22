@echo off
chcp 65001 >nul
title xyc Launcher
setlocal EnableExtensions
rem xyc Launcher - universal starter (works on any PC):
rem  1) uses the bundled runtime if present (so copying the whole folder works even with no system Java)
rem  2) else JAVA_HOME / common install dirs
rem  3) else auto-downloads Java 25 runtime, then starts. Never touches PATH java.
set "ROOT=%~dp0"
set "FOUND="

rem 1) bundled runtime (.minecraft\runtime\jdk-*\jdk-*\bin\java.exe)
for /d %%R in ("%ROOT%.minecraft\runtime\jdk-*") do (
    if not defined FOUND for /d %%J in ("%%R\jdk-*") do (
        if not defined FOUND if exist "%%J\bin\java.exe" set "FOUND=%%J\bin\java.exe"
    )
)

rem 2) JAVA_HOME
if not defined FOUND if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "FOUND=%JAVA_HOME%\bin\java.exe"

rem 3) common install dirs
for %%R in ("%ProgramFiles%\Java" "%ProgramFiles(x86)%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles(x86)%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles(x86)%\Microsoft" "%ProgramFiles(x86)%\Android\openjdk" "%ProgramFiles%\Zulu" "%ProgramFiles%\Amazon Corretto") do (
    if not defined FOUND if exist "%%~R" (
        for /d %%D in ("%%~R\*") do (
            if not defined FOUND if exist "%%~D\bin\java.exe" (
                "%%~D\bin\java.exe" -version >nul 2>&1
                if not errorlevel 1 set "FOUND=%%~D\bin\java.exe"
            )
        )
    )
)

rem 4) auto-download Java 25 if nothing found
if not defined FOUND (
    echo No Java found. Downloading Java 25 runtime (~200MB), please wait...
    set "RUNTIME=%ROOT%.minecraft\runtime\jdk-25"
    if not exist "%RUNTIME%" mkdir "%RUNTIME%"
    if exist "%RUNTIME%\jdk-25.zip" del /q "%RUNTIME%\jdk-25.zip"
    curl.exe -L --retry 3 -o "%RUNTIME%\jdk-25.zip" "https://aka.ms/download-jdk/microsoft-jdk-25-windows-x64.zip"
    if exist "%RUNTIME%\jdk-25.zip" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%RUNTIME%\jdk-25.zip' -DestinationPath '%RUNTIME%' -Force"
        del /q "%RUNTIME%\jdk-25.zip"
    )
    for /d %%J in ("%RUNTIME%\jdk-*") do if not defined FOUND if exist "%%J\bin\java.exe" set "FOUND=%%J\bin\java.exe"
)

if not defined FOUND (
    echo Unable to get Java. Check your network / internet and retry, or install Java manually.
    pause
    exit /b 1
)
echo Using Java: %FOUND%
"%FOUND%" -jar "%ROOT%launcher.jar"
if errorlevel 1 pause
endlocal