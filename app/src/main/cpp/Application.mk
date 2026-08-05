# lide native engine (AGP externalNativeBuild / ndk-build)
# APP_ABI is injected by AGP from product flavor abiFilters (x64/x86_64, arm64/arm64-v8a).
# STL: live2d/luv use C++; match the former vendored build (c++_static, release, android-24).
APP_PLATFORM := android-24
APP_STL := c++_static
APP_OPTIM := release