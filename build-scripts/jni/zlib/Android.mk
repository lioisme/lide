LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := zlib
LOCAL_SRC_FILES  := lua_zlib.c
LOCAL_CFLAGS     := -Wno-unused-parameter -Wno-unused-variable
LOCAL_LDLIBS    := -lz
LOCAL_SHARED_LIBRARIES := luajava

include $(BUILD_SHARED_LIBRARY)
