#!/bin/bash
# delua AndroLua Engine Rebuild Script (Unix/macOS)
# Usage: ./scripts/rebuild_engine.sh [arm64-v8a|armeabi-v7a|x86_64|all]
# Requires: ANDROID_NDK_HOME, Android SDK (for d8), Java 8+

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DELUA_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PREBUILT_DIR="$DELUA_ROOT/prebuilt"
JNI_REF_DIR="$DELUA_ROOT/docs/AndroLua_pro-master/app/src/main/jni"
JAVA_REF_DIR="$DELUA_ROOT/docs/AndroLua_pro-master/app/src/main/java"
BUILD_DIR="${TMPDIR:-/tmp}/delua-ndk-build"
ANDROID_JAR="${ANDROID_HOME}/platforms/android-24/android.jar"

ABI="${1:-all}"

# Validate inputs
case "$ABI" in
    arm64-v8a|armeabi-v7a|x86_64|all) ;;
    *) echo "Usage: $0 [arm64-v8a|armeabi-v7a|x86_64|all]"; exit 1 ;;
esac

# Verify NDK
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set"
    echo "Set it to your NDK installation path, e.g.:"
    echo "  export ANDROID_NDK_HOME=\$HOME/Android/Sdk/ndk/26.1.10909125"
    exit 1
fi

NDK_BUILD="$ANDROID_NDK_HOME/ndk-build"
if [ ! -f "$NDK_BUILD" ] && [ ! -f "${ANDROID_NDK_HOME}/ndk-build" ]; then
    echo "ERROR: ndk-build not found in $ANDROID_NDK_HOME"
    exit 1
fi

# Verify Android SDK
if [ -z "${ANDROID_HOME:-}" ]; then
    echo "ERROR: ANDROID_HOME is not set"
    exit 1
fi

echo "============================================"
echo " delua Engine Rebuild Script"
echo " ABI:       $ABI"
echo " NDK:       $ANDROID_NDK_HOME"
echo " SDK:       $ANDROID_HOME"
echo " Output:    $PREBUILT_DIR"
echo "============================================"

# Step 1: Clean and prepare build directory
echo ""
echo "[1/5] Preparing build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/jni/lua"
mkdir -p "$BUILD_DIR/jni/luajava"
mkdir -p "$BUILD_DIR/jni/luagl"

# Step 2: Copy C sources
echo "[2/5] Copying C sources..."
cp "$JNI_REF_DIR/lua/"*.c "$BUILD_DIR/jni/lua/"
cp "$JNI_REF_DIR/lua/"*.h "$BUILD_DIR/jni/lua/"
cp "$JNI_REF_DIR/luajava/luajava.c" "$BUILD_DIR/jni/luajava/"
cp "$JNI_REF_DIR/luajava/luajava.h" "$BUILD_DIR/jni/luajava/"
cp "$JNI_REF_DIR/luagl/"*.c "$BUILD_DIR/jni/luagl/"
cp "$JNI_REF_DIR/luagl/"*.h "$BUILD_DIR/jni/luagl/"

# Remove standalone executables (they have main())
rm -f "$BUILD_DIR/jni/lua/lua.c" "$BUILD_DIR/jni/lua/luac.c"

# Step 3: Write build configs
echo "[3/5] Writing NDK build configs..."

cat > "$BUILD_DIR/jni/Android.mk" << 'MAKEFILE'
include $(call all-subdir-makefiles)
MAKEFILE

write_app_mk() {
    local abi_list="$1"
    cat > "$BUILD_DIR/jni/Application.mk" << APPMK
APP_ABI := $abi_list
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release
APPMK
}

write_lua_mk() {
    cat > "$BUILD_DIR/jni/lua/Android.mk" << 'LUAMK'
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := lua
LOCAL_CFLAGS := -std=c99 -DLUA_DL_DLOPEN
LOCAL_SRC_FILES := \
    lapi.c lauxlib.c lbaselib.c lbitlib.c lcode.c \
    lcorolib.c lctype.c ldblib.c ldebug.c ldo.c \
    ldump.c lfunc.c lgc.c linit.c liolib.c \
    llex.c lmathlib.c lmem.c loadlib.c lobject.c \
    lopcodes.c loslib.c lparser.c lstate.c lstring.c \
    lstrlib.c ltable.c ltablib.c ltm.c lundump.c \
    lutf8lib.c lvm.c lzio.c
LOCAL_LDLIBS := -llog -ldl -lm
include $(BUILD_STATIC_LIBRARY)
LUAMK
}

write_luajava_mk() {
    cat > "$BUILD_DIR/jni/luajava/Android.mk" << 'LJAVAMK'
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := luajava
LOCAL_SRC_FILES := luajava.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_CFLAGS := -std=c99 -Wno-pointer-to-int-cast -Wno-int-to-pointer-cast -Wno-pointer-sign
LOCAL_LDLIBS := -llog -ldl
LOCAL_STATIC_LIBRARIES := lua
include $(BUILD_SHARED_LIBRARY)
LJAVAMK
}

write_gl_mk() {
    cat > "$BUILD_DIR/jni/luagl/Android.mk" << 'GLMK'
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := gl
LOCAL_SRC_FILES := lgl.c lgl_const.c lgl_util.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua $(LOCAL_PATH)/../luajava
LOCAL_CFLAGS := -std=c99 -DANDROID_NDK -DDISABLE_IMPORTGL -Wno-implicit-function-declaration
LOCAL_LDLIBS := -lGLESv1_CM -ldl -llog
LOCAL_STATIC_LIBRARIES := luajava
include $(BUILD_SHARED_LIBRARY)
GLMK
}

if [ "$ABI" = "all" ]; then
    write_app_mk "arm64-v8a armeabi-v7a x86_64"
else
    write_app_mk "$ABI"
fi
write_lua_mk
write_luajava_mk
write_gl_mk

# Step 4: Run NDK build
echo "[4/5] Running ndk-build (ABI=$ABI)..."
cd "$BUILD_DIR"
export NDK_PROJECT_PATH="$BUILD_DIR"
$NDK_BUILD -j4
echo "ndk-build completed successfully"

# Step 5: Copy artifacts to prebuilt/
echo "[5/5] Copying artifacts to prebuilt/..."
declare -a ABI_LIST
if [ "$ABI" = "all" ]; then
    ABI_LIST=("arm64-v8a" "armeabi-v7a" "x86_64")
else
    ABI_LIST=("$ABI")
fi

for abi in "${ABI_LIST[@]}"; do
    mkdir -p "$PREBUILT_DIR/$abi"
    cp "$BUILD_DIR/libs/$abi/libluajava.so" "$PREBUILT_DIR/$abi/"
    if [ -f "$BUILD_DIR/libs/$abi/libgl.so" ]; then
        cp "$BUILD_DIR/libs/$abi/libgl.so" "$PREBUILT_DIR/$abi/"
    fi
done

# Also update jniLibs for direct APK builds
for abi in "${ABI_LIST[@]}"; do
    JNILIBS_DIR="$DELUA_ROOT/app/src/main/jniLibs/$abi"
    mkdir -p "$JNILIBS_DIR"
    cp "$BUILD_DIR/libs/$abi/libluajava.so" "$JNILIBS_DIR/"
    if [ -f "$BUILD_DIR/libs/$abi/libgl.so" ]; then
        cp "$BUILD_DIR/libs/$abi/libgl.so" "$JNILIBS_DIR/"
    fi
done

echo ""
echo "=== Build Summary ==="
find "$PREBUILT_DIR" -name "*.so" -exec ls -lh {} \;
echo ""
echo "Engine rebuild complete."
echo "Run 'git add prebuilt/ app/src/main/jniLibs/' to stage new artifacts."
