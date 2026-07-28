@echo off
if "%1"=="/c" goto :clean
if "%1"=="-clean" goto :clean

echo === Building 4 APKs ===
echo.
pushd "%~dp0"

pushd "%~dp0"

echo [1/4] x64Debug
D:\Gradle\gradle-9.5.1\bin\gradle.bat :app:assembleX64Debug
echo [2/4] x64Release
D:\Gradle\gradle-9.5.1\bin\gradle.bat :app:assembleX64Release
echo [3/4] arm64Debug
D:\Gradle\gradle-9.5.1\bin\gradle.bat :app:assembleArm64Debug
echo [4/4] arm64Release
D:\Gradle\gradle-9.5.1\bin\gradle.bat :app:assembleArm64Release

popd

popd
echo.
echo === All 4 APKs built ===
echo.
echo Outputs:
echo   x64Debug    app\build\outputs\apk\x64\debug\app-x64-debug.apk
echo   x64Release  app\build\outputs\apk\x64\release\app-x64-release.apk
echo   arm64Debug  app\build\outputs\apk\arm64\debug\app-arm64-debug.apk
echo   arm64Release app\build\outputs\apk\arm64\release\app-arm64-release.apk
goto :eof

:clean
pushd "%~dp0"
D:\Gradle\gradle-9.5.1\bin\gradle.bat clean
popd
echo Done.
goto :eof