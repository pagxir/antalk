LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := madplay
LOCAL_ARM_MODE  := arm
LOCAL_SRC_FILES :=	mad/bit.c \
					mad/decoder.c \
					mad/fixed.c \
					mad/frame.c \
					mad/huffman.c \
					mad/layer12.c \
					mad/layer3.c \
					mad/stream.c \
					mad/synth.c \
					mad/timer.c \
					mad/version.c \
					wrapper.c

LOCAL_CFLAGS := -DHAVE_CONFIG_H -ffast-math -O3
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_CFLAGS += -DFPM_ARM 
endif

ifeq ($(TARGET_ARCH_ABI),armeabi)
	LOCAL_CFLAGS += -DFPM_ARM
endif

ifeq ($(TARGET_ARCH_ABI),mips)
	LOCAL_CFLAGS += -DFPM_DEFAULT
endif

ifeq ($(TARGET_ARCH_ABI),x86)
	LOCAL_CFLAGS += -DFPM_INTEL
endif

#LOCAL_LDFLAGS := -static

include $(BUILD_SHARED_LIBRARY)
#include $(BUILD_EXECUTABLE)

