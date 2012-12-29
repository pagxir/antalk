LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := shine
LOCAL_CFLAGS := -DHAVE_CONFIG_H -ffast-math -O3
LOCAL_SRC_FILES :=	bitstream.c \
					coder.c \
					huffman.c \
					layer3.c \
					loop.c \
					shine.c \
					wrapper.c \
					main.c
LOCAL_LDLIBS := 

include $(BUILD_SHARED_LIBRARY)

