#!/bin/bash
# lide AndroLua Engine Rebuild Script (Unix/macOS)
# Usage: ./scripts/rebuild_engine.sh [arm64-v8a|armeabi-v7a|x86_64|all]
# Requires: ANDROID_NDK_HOME

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIDE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PREBUILT_DIR="$LIDE_ROOT/third_party/prebuilt"
JNILIBS_DIR="$LIDE_ROOT/app/src/main/jniLibs"
JNI_SRC_DIR="$LIDE_ROOT/docs/reference/androlua/AndroLua_pro-master/app/src/main/jni"
JNI_CFG_DIR="$LIDE_ROOT/build-scripts/jni"
BUILD_DIR="${TMPDIR:-/tmp}/lide-ndk-build"
BUILD_LOG="$BUILD_DIR/build.log"

# Disabled modules kept in reference tree (Android.mk2 only / no build file).
# Note: lfs & lsqlite3 are enabled (build-scripts/jni has Android.mk).
# lluv is superseded by luv (already shipped); tcc lacks arm64/x86_64 targets.
DISABLED_MODULES=("lluv" "tcc")

EXPECTED_MODULES=("cjson" "md5" "crypt" "zip" "xml" "regex"
                  "zlib" "canvas" "bson" "luv" "socket" "mime"
                  "sensor" "gl" "lfs" "lsqlite3")

ABI="${1:-all}"

case "$ABI" in
    arm64-v8a|armeabi-v7a|x86_64|all) ;;
    *) echo "Usage: $0 [arm64-v8a|armeabi-v7a|x86_64|all]"; exit 1 ;;
esac

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set"
    exit 1
fi

echo "============================================"
echo " lide Engine Rebuild Script"
echo " ABI:       $ABI"
echo " NDK:       $ANDROID_NDK_HOME"
echo " Output:    $PREBUILT_DIR"
echo "============================================"

# Step 1: Clean and prepare build directory
echo ""
echo "[1/5] Preparing build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Copy reference JNI source tree (docs/AndroLua_pro-master), then overlay
# lide-managed Android.mk build configs from build-scripts/jni/
cp -R "$JNI_SRC_DIR" "$BUILD_DIR/jni"

while IFS= read -r mk; do
    rel_path="${mk#"$JNI_CFG_DIR"/}"
    cp "$mk" "$BUILD_DIR/jni/$rel_path"
done < <(find "$JNI_CFG_DIR" -name "Android.mk")

# Remove disabled module directories (Android.mk2 only / no build file)
for dir in "${DISABLED_MODULES[@]}"; do
    if [ -d "$BUILD_DIR/jni/$dir" ]; then
        rm -rf "$BUILD_DIR/jni/$dir"
    fi
done

# Remove standalone executables (they have main())
rm -f "$BUILD_DIR/jni/lua/lua.c" "$BUILD_DIR/jni/lua/luac.c"

# Step 2: Write Application.mk with modern settings
echo "[2/5] Writing Application.mk..."
if [ "$ABI" = "all" ]; then
    ABI_LIST="arm64-v8a armeabi-v7a x86_64"
else
    ABI_LIST="$ABI"
fi

cat > "$BUILD_DIR/jni/Application.mk" << APPMK
APP_ABI := $ABI_LIST
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release
APPMK

# List all modules that will be built
echo ""
echo "Modules to build:"
for mk in "$BUILD_DIR/jni"/*/Android.mk; do
    dir=$(dirname "$mk")
    mod=$(basename "$dir")
    echo "  - $mod ($(grep 'LOCAL_MODULE ' "$mk" | head -1 | sed 's/.*:= //'))"
done

# Step 3: Run NDK build with output capture
echo ""
echo "[3/5] Running ndk-build (ABI=$ABI)..."
cd "$BUILD_DIR"
export NDK_PROJECT_PATH="$BUILD_DIR"
$ANDROID_NDK_HOME/ndk-build -j4 2>&1 | tee "$BUILD_LOG"
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo ""
    echo "=== BUILD FAILED ==="
    echo "Check build log: $BUILD_LOG"
    exit 1
fi

# Step 4: Copy artifacts to prebuilt/ and jniLibs/
echo ""
echo "[4/5] Copying artifacts to prebuilt/ and jniLibs/..."

declare -a ABI_LIST_ARR
if [ "$ABI" = "all" ]; then
    ABI_LIST_ARR=("arm64-v8a" "armeabi-v7a" "x86_64")
else
    ABI_LIST_ARR=("$ABI")
fi

TOTAL_EXPECTED=0
SUCCESS_COUNT=0
FAILED_MODULES=()

for abi in "${ABI_LIST_ARR[@]}"; do
    libs_dir="$BUILD_DIR/libs/$abi"
    if [ ! -d "$libs_dir" ]; then
        echo "  WARNING: No libraries built for $abi"
        continue
    fi

    # Collect produced module names
    produced=()
    for so in "$libs_dir"/*.so; do
        name=$(basename "$so" .so)
        name="${name#lib}"
        produced+=("$name")
    done

    # Copy to prebuilt/
    mkdir -p "$PREBUILT_DIR/$abi"
    for so in "$libs_dir"/*.so; do
        if [ -f "$so" ]; then
            cp "$so" "$PREBUILT_DIR/$abi/"
            echo "  third_party/prebuilt/$abi/$(basename "$so")"
        fi
    done

    # Copy to jniLibs/
    mkdir -p "$JNILIBS_DIR/$abi"
    for so in "$libs_dir"/*.so; do
        if [ -f "$so" ]; then
            cp "$so" "$JNILIBS_DIR/$abi/"
        fi
    done

    # Validate expected modules
    for mod in "${EXPECTED_MODULES[@]}"; do
        TOTAL_EXPECTED=$((TOTAL_EXPECTED + 1))
        if printf '%s\n' "${produced[@]}" | grep -qx "$mod"; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        else
            FAILED_MODULES+=("$abi/$mod")
        fi
    done
done

# Step 5: Summary
echo ""
echo "[5/5] Build Summary"
echo ""
find "$PREBUILT_DIR" -name "*.so" -exec ls -lh {} \; 2>/dev/null || true
echo ""
echo "Build summary: $SUCCESS_COUNT/$TOTAL_EXPECTED modules succeeded"
if [ ${#FAILED_MODULES[@]} -gt 0 ]; then
    echo "Missing modules:"
    for fm in "${FAILED_MODULES[@]}"; do
        echo "  FAILED: $fm"
    done
    echo ""
    echo "Detailed build log: $BUILD_LOG"
    exit 1
fi
echo ""
echo "Engine rebuild complete."
