@echo off
rem Build 6 APKs (x64/arm64/universal x Debug/Release) as independent variant tasks.
rem Self-signed with project-local keystore,
rem environment-neutral: project gradlew wrapper only, no absolute Gradle path.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apks.ps1" %*
exit /b %ERRORLEVEL%