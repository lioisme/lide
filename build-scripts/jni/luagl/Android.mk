LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := gl
LOCAL_SRC_FILES := luagl.c luagl_const.c luagl_util.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua $(LOCAL_PATH)/../luajava
LOCAL_CFLAGS := -std=c99 -DANDROID_NDK -DDISABLE_IMPORTGL -Wno-implicit-function-declaration
LOCAL_LDLIBS := -lGLESv1_CM -ldl -llog
LOCAL_SHARED_LIBRARIES := luajava
include $(BUILD_SHARED_LIBRARY)
