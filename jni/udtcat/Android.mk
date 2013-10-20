LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE    := udtcat
LOCAL_SRC_FILES := api.cpp buffer.cpp cache.cpp ccc.cpp channel.cpp common.cpp core.cpp epoll.cpp list.cpp md5.cpp udtcat.cpp packet.cpp queue.cpp window.cpp

LOCAL_C_INCLUDES +=  $(JNI_H_INCLUDE) $(LOCAL_PATH)/
LOCAL_CXX_INCLUDES +=  $(JNI_H_INCLUDE) $(LOCAL_PATH)/
LOCAL_STATIC_LIBRARIES = liblog
LOCAL_LDLIBS += -llog
LOCAL_CXXFLAGS += -fexceptions -DLINUX -fPIC -Wall -Wextra -finline-functions -O3 -fno-strict-aliasing -fvisibility=hidden

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE    := tcpcat
LOCAL_SRC_FILES := tcpcat.cpp
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE    := tuncat
LOCAL_SRC_FILES := tuncat.cpp
include $(BUILD_EXECUTABLE)
