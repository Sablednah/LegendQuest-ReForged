@echo off
rem ---------------------------------------------------------------------
rem TestClient.cmd — launch the TestBuddy dev client for multiplayer
rem testing. Double-click me, or run from PowerShell: .\TestClient.cmd
rem
rem Uses CurseForge's bundled JDK 21 and a separate gradle project cache
rem (.gradle-win) so it never fights the WSL-side server worktree.
rem Pair with the dev server: run `./gradlew runServer` in
rem ..\LegendQuest-ReForged-srv from WSL, then Direct Connect: localhost
rem ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"
set "JAVA_HOME=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Could not find CurseForge's JDK 21 at %JAVA_HOME%
    echo Edit JAVA_HOME in this file to point at any JDK 21.
    pause
    exit /b 1
)
echo Starting TestBuddy dev client (first run compiles - be patient)...
call gradlew.bat runClientBuddy --project-cache-dir .gradle-win
pause
