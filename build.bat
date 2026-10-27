@echo off
setlocal
cd /d "%~dp0"
if not exist "build\classes" mkdir "build\classes"
if exist "build\sources.txt" del "build\sources.txt"
powershell -NoProfile -Command "Get-ChildItem -Path 'src\main\java' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + $_.FullName.Replace([char]92,'/') + [char]34 } | Set-Content -Encoding ASCII 'build\sources.txt'"
echo Compiling KIU digitalQ...
javac -encoding UTF-8 --release 17 -d "build\classes" "@build\sources.txt"
if errorlevel 1 (
    echo.
    echo Build failed. Make sure a JDK 17 or newer is installed and on PATH.
    exit /b 1
)
echo Build complete: build\classes
