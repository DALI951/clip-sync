@echo off
cd /d "%~dp0"
echo Installing dependencies...
call npm install
echo Starting ClipSync PC service...
node server.js
pause
