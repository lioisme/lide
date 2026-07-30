# delua AndroLua 引擎重建脚本
# 用法: .\scripts\rebuild_engine.ps1 [-ABI <arm64-v8a|armeabi-v7a|all>]
# 要求: ANDROID_NDK_HOME 环境变量 或 NDK 位于 D:\Android\ndk\<version>

param(
    [ValidateSet("arm64-v8a", "armeabi-v7a", "x86_64", "all")]
    [string]$ABI = "all"
)

$ErrorActionPreference = "Stop"
$DELUA_ROOT = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$PREBUILT_DIR = Join-Path $DELUA_ROOT "prebuilt"
$JNI_REF_DIR = Join-Path $DELUA_ROOT "docs\AndroLua_pro-master\app\src\main\jni"
$BUILD_DIR = Join-Path $env:TEMP "delua-ndk-build"

# 1. 定位 NDK
$NDK_DIR = $env:ANDROID_NDK_HOME
if (-not $NDK_DIR) {
    $candidates = @(
        "D:\Android\ndk\29.0.14206865",
        "D:\Android\ndk\28.2.13676358",
        "D:\Android\ndk\27.0.12077973",
        "D:\Android\ndk\25.1.8937393",
        "$env:LOCALAPPDATA\Android\Sdk\ndk\*"
    )
    foreach ($c in $candidates) {
        $resolved = Resolve-Path $c -ErrorAction SilentlyContinue
        if ($resolved) { $NDK_DIR = $resolved[-1].Path; break }
    }
}
if (-not $NDK_DIR -or -not (Test-Path (Join-Path $NDK_DIR "ndk-build.cmd"))) {
    Write-Error "NDK not found. Set ANDROID_NDK_HOME or install NDK."
    exit 1
}
Write-Host "[1/5] NDK: $NDK_DIR"

# 2. 清理并准备构建目录
if (Test-Path $BUILD_DIR) { Remove-Item $BUILD_DIR -Recurse -Force }
$JNI_DIR = Join-Path $BUILD_DIR "jni"
New-Item -ItemType Directory -Path "$JNI_DIR\lua" -Force | Out-Null
New-Item -ItemType Directory -Path "$JNI_DIR\luajava" -Force | Out-Null
New-Item -ItemType Directory -Path "$JNI_DIR\luagl" -Force | Out-Null

# 3. 复制 C 源码
Write-Host "[2/5] Copying C sources..."
Copy-Item "$JNI_REF_DIR\lua\*.c" "$JNI_DIR\lua\" -Force
Copy-Item "$JNI_REF_DIR\lua\*.h" "$JNI_DIR\lua\" -Force
Copy-Item "$JNI_REF_DIR\luajava\luajava.c" "$JNI_DIR\luajava\" -Force
Copy-Item "$JNI_REF_DIR\luajava\luajava.h" "$JNI_DIR\luajava\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl.c" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl.h" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luaglu.c" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luaglu.h" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl_const.c" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl_const.h" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl_util.c" "$JNI_DIR\luagl\" -Force
Copy-Item "$JNI_REF_DIR\luagl\luagl_util.h" "$JNI_DIR\luagl\" -Force

# 移除 lua.c 和 luac.c（独立可执行文件，含 main()）
Remove-Item "$JNI_DIR\lua\lua.c" -Force -ErrorAction SilentlyContinue
Remove-Item "$JNI_DIR\lua\luac.c" -Force -ErrorAction SilentlyContinue

# 4. 写入构建配置
Write-Host "[3/5] Writing build config..."
@"
include `$(call all-subdir-makefiles)
"@ | Set-Content (Join-Path $JNI_DIR "Android.mk") -Encoding ASCII

@"
APP_ABI := $ABI
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release
APP_ALLOW_MISSING_DEPS := true
"@ | Set-Content (Join-Path $JNI_DIR "Application.mk") -Encoding ASCII

@"
LOCAL_PATH := `$(call my-dir)
include `$(CLEAR_VARS)
LOCAL_MODULE := lua
LOCAL_CFLAGS := -std=c99 -DLUA_DL_DLOPEN
LOCAL_SRC_FILES := $((Get-ChildItem "$JNI_DIR\lua\*.c" -Name | ForEach-Object { "`t$_ `\"`\" + `n" }) -join "")
LOCAL_LDLIBS := -llog -ldl -lm
include `$(BUILD_STATIC_LIBRARY)
"@ | Set-Content (Join-Path $JNI_DIR "lua\Android.mk") -Encoding ASCII

@"
LOCAL_PATH := `$(call my-dir)
include `$(CLEAR_VARS)
LOCAL_MODULE := luajava
LOCAL_SRC_FILES := luajava.c
LOCAL_C_INCLUDES += `$(LOCAL_PATH)/../lua
LOCAL_CFLAGS := -std=c99 -Wno-pointer-to-int-cast -Wno-int-to-pointer-cast -Wno-pointer-sign
LOCAL_LDLIBS := -llog -ldl
LOCAL_STATIC_LIBRARIES := lua
include `$(BUILD_SHARED_LIBRARY)
"@ | Set-Content (Join-Path $JNI_DIR "luajava\Android.mk") -Encoding ASCII

@"
LOCAL_PATH := `$(call my-dir)
include `$(CLEAR_VARS)
LOCAL_MODULE := gl
LOCAL_SRC_FILES := luagl.c luagl_const.c luagl_util.c
LOCAL_C_INCLUDES += `$(LOCAL_PATH)/../lua `$(LOCAL_PATH)/../luajava
LOCAL_CFLAGS := -std=c99 -DANDROID_NDK -DDISABLE_IMPORTGL -Wno-implicit-function-declaration
LOCAL_LDLIBS := -lGLESv1_CM -ldl -llog
LOCAL_STATIC_LIBRARIES := luajava
include `$(BUILD_SHARED_LIBRARY)
"@ | Set-Content (Join-Path $JNI_DIR "luagl\Android.mk") -Encoding ASCII

# 5. 执行 NDK 编译
$NDK_BUILD = Join-Path $NDK_DIR "ndk-build.cmd"
Write-Host "[4/5] Running ndk-build (ABI=$ABI)..."
$env:NDK_PROJECT_PATH = $BUILD_DIR
$process = Start-Process -FilePath $NDK_BUILD -ArgumentList "-j4" -NoNewWindow -Wait -PassThru
if ($process.ExitCode -ne 0) {
    Write-Error "ndk-build failed with exit code $($process.ExitCode)"
    exit $process.ExitCode
}

# 6. 复制产物到 prebuilt/
Write-Host "[5/5] Copying artifacts to prebuilt/..."
$ABI_LIST = if ($ABI -eq "all") { @("arm64-v8a", "armeabi-v7a") } else { @($ABI) }
foreach ($abi in $ABI_LIST) {
    $target = Join-Path $PREBUILT_DIR $abi
    New-Item -ItemType Directory $target -Force | Out-Null
    Copy-Item "$BUILD_DIR\libs\$abi\libluajava.so" $target -Force
    Copy-Item "$BUILD_DIR\libs\$abi\libgl.so" $target -Force -ErrorAction SilentlyContinue
}

Write-Host "`n=== Build Summary ==="
Get-ChildItem $PREBUILT_DIR -Recurse -Filter "*.so" | ForEach-Object {
    $size = [math]::Round($_.Length / 1KB, 1)
    Write-Host "  $($_.Directory.Name)/$($_.Name)  ${size}KB"
}
Write-Host "`n✅ Engine rebuild complete"
