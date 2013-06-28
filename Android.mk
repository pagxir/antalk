LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_CERTIFICATE := platform
LOCAL_PACKAGE_NAME := antalk
LOCAL_JNI_SHARED_LIBRARIES := libpstcp

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
include $(call all-makefiles-under,$(LOCAL_PATH))


