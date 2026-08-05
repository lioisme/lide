LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := luajava
LOCAL_SRC_FILES := luajava.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_CFLAGS := -std=c99 -Wno-pointer-to-int-cast -Wno-int-to-pointer-cast -Wno-pointer-sign
LOCAL_LDLIBS := -llog -ldl
LOCAL_STATIC_LIBRARIES := lua
include $(BUILD_SHARED_LIBRARY)
