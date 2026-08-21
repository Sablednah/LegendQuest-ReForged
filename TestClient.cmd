@echo off
rem ---------------------------------------------------------------------
rem TestClient.cmd — launch the TestBuddy dev client for multiplayer
rem testing. Double-click me, or run from PowerShell: .\TestClient.cmd
rem
rem Uses CurseForge's bundled JDK 21 and a separate gradle project cache
rem (.gradle-win) so it never fights the WSL-side server worktree.
rem Pair with the dev server: run `./gradlew runServer` in
rem ..\LegendQuest-ReForged-srv from WSL, then Direct Connect: 127.0.0.1 (NOT localhost - it resolves IPv6 first)
rem ---------------------------------------------------------------------
setlocal
rem The buddy lives in its OWN worktree so its gradle build never fights
rem the main repo's jar builds or the WSL server worktree over artifacts.
cd /d "%~dp0..\LegendQuest-ReForged-buddy"
set "JAVA_HOME=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Could not find CurseForge's JDK 21 at %JAVA_HOME%
    echo Edit JAVA_HOME in this file to point at any JDK 21.
    pause
    exit /b 1
)
rem Silence every sound category before launching. The buddy exists to be
rem driven from a script while its owner is doing something else -- quite
rem possibly on a call -- and a dev client that starts playing the Minecraft
rem menu music into a meeting is its own kind of bug. The client rewrites
rem options.txt on exit, so this is enforced on every launch rather than set
rem once. Bumping master alone would do it, but zeroing each category means
rem nudging the master slider in-game does not undo the whole thing.
if exist "runBuddy\options.txt" powershell -NoProfile -Command "$f='runBuddy\options.txt'; (Get-Content $f) -replace '^(soundCategory_[a-zA-Z]+):.*$', '$1:0.0' | Set-Content $f"

echo Starting TestBuddy dev client (first run compiles - be patient)...
call gradlew.bat runClientBuddy --project-cache-dir .gradle-win
pause
