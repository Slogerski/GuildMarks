@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean remapJar --console=plain
if errorlevel 1 exit /b %errorlevel%
if not exist release\modrinth mkdir release\modrinth
copy /Y "build\libs\GuildMark-1.21.8-1.0.1.jar" "release\modrinth\GuildMark-1.21.8-1.0.1.jar" >nul
echo Built release\modrinth\GuildMark-1.21.8-1.0.1.jar
endlocal
