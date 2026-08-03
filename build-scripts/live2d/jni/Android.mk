# Live2D V2 native engine + Lua binding (lide)
# Ported from the l2d-py Live2D/V2 reference (C++/GLES2).
#
# Single entry point (Lua -> C direct, no Java bridge):
#   - luaopen_live2d (require "live2d" via package.cpath)
#
# Lua API comes from the host's libluajava.so (shared), same convention as
# libcjson.so: the module only receives lua_State* and must not embed a
# second Lua VM.

LOCAL_PATH := $(call my-dir)

# Host engine libluajava.so provides the Lua VM (same convention as libcjson.so)
include $(CLEAR_VARS)
LOCAL_MODULE := luajava
LOCAL_SRC_FILES := ../../../app/src/main/jniLibs/$(TARGET_ARCH_ABI)/libluajava.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := live2d

LOCAL_SRC_FILES := \
    ../src/Core/Id.cpp \
    ../src/Core/BinaryReader.cpp \
    ../src/Core/Live2DObjectFactory.cpp \
    ../src/Core/ParamDefFloat.cpp \
    ../src/Core/ParamDefSet.cpp \
    ../src/Core/PartsData.cpp \
    ../src/Core/PartsDataContext.cpp \
    ../src/Core/Avatar.cpp \
    ../src/Core/ModelImpl.cpp \
    ../src/Core/ParamPivots.cpp \
    ../src/Core/PivotManager.cpp \
    ../src/Model/ModelContext.cpp \
    ../src/Model/ALive2DModel.cpp \
    ../src/Model/Live2DModelOpenGL.cpp \
    ../src/Deformer/Deformer.cpp \
    ../src/Deformer/AffineEnt.cpp \
    ../src/Deformer/RotationDeformer.cpp \
    ../src/Deformer/RotationContext.cpp \
    ../src/Deformer/WarpDeformer.cpp \
    ../src/Deformer/WarpContext.cpp \
    ../src/Draw/IDrawData.cpp \
    ../src/Draw/Mesh.cpp \
    ../src/Graphics/DrawParamOpenGL.cpp \
    ../src/Graphics/ClippingManagerOpenGL.cpp \
    ../src/Graphics/ClipContext.cpp \
    ../src/Framework/L2DMatrix44.cpp \
    ../src/Framework/L2DModelMatrix.cpp \
    ../src/Framework/L2DTargetPoint.cpp \
    ../src/Framework/L2DEyeBlink.cpp \
    ../src/Framework/L2DPose.cpp \
    ../src/Framework/L2DPhysics.cpp \
    ../src/Framework/PhysicsHair.cpp \
    ../src/Framework/L2DExpressionMotion.cpp \
    ../src/Framework/L2DMotionManager.cpp \
    ../src/Framework/L2DBaseModel.cpp \
    ../src/Framework/MatrixManager.cpp \
    ../src/Motion/Live2DMotion.cpp \
    ../src/Util/UtInterpolate.cpp \
    ../src/Util/UtSystem.cpp \
    ../src/Util/stb_impl.cpp \
    ../src/Common/Log.cpp \
    ../src/LAppModel.cpp \
    ../src/lua/lua_live2d.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../src $(LOCAL_PATH)/../src/Common $(LOCAL_PATH)/../../jni/lua

LOCAL_CPPFLAGS := -std=c++17 -O2 -Wall -DCSM_TARGET_ANDROID_ES2
LOCAL_CPP_FEATURES := exceptions

LOCAL_LDLIBS := -lGLESv2 -llog
LOCAL_SHARED_LIBRARIES := luajava

include $(BUILD_SHARED_LIBRARY)

