# delua liblive2d.so build config
# Cross-compiled with the same NDK toolchain as the androlua native libs
# (see build-scripts/jni/Application.mk for the canonical flags).
# Output is kept OUT of prebuilt/ and app/src/main/jniLibs/ (original lib
# dirs); the app consumes it via an extra jniLibs srcDir instead.
APP_ABI := arm64-v8a armeabi-v7a x86_64
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release
