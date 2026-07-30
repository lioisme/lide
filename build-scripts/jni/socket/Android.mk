LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE := socket
LOCAL_CFLAGS := -Wno-unused-parameter -Wno-unused-variable
LOCAL_SRC_FILES := \
	luasocket.c \
	timeout.c \
	buffer.c \
	io.c \
	auxiliar.c \
	options.c \
	inet.c \
	tcp.c \
	udp.c \
	except.c \
	select.c \
	usocket.c
LOCAL_STATIC_LIBRARIES := luajava
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE := mime
LOCAL_CFLAGS := -Wno-unused-parameter -Wno-unused-variable
LOCAL_SRC_FILES := mime.c
LOCAL_STATIC_LIBRARIES := luajava
include $(BUILD_SHARED_LIBRARY)
