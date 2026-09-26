@echo off
cd /d "%~dp0"
call mvn javafx:run
if errorlevel 1 pause
