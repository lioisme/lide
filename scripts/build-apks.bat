@echo off
rem Build 4 APKs (x64/arm64 x Debug/Release) as independent variant tasks.
rem No merged/universal build, self-signed with project-local keystore,
rem environment-neutral: project gradlew wrapper only, no absolute Gradle path.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apks.ps1" %*
exit /b %ERRORLEVEL%