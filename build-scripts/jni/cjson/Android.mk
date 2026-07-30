LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE := cjson
LOCAL_SRC_FILES := \
	lua_cjson.c \
	fpconv.c \
	strbuf.c
LOCAL_CFLAGS := -Wno-unused-parameter -Wno-unused-variable
LOCAL_STATIC_LIBRARIES := luajava
include $(BUILD_SHARED_LIBRARY)
