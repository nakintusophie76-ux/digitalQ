@echo off
setlocal
cd /d "%~dp0"
if not exist "build\classes\com\kiu\digitalq\ServerMain.class" call "build.bat"
if errorlevel 1 exit /b 1
java -cp "build\classes" com.kiu.digitalq.ServerMain %*
