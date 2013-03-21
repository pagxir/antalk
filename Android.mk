LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_CERTIFICATE := platform
LOCAL_PACKAGE_NAME := antalk
LOCAL_JNI_SHARED_LIBRARIES := libmad libshine

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
include $(call all-makefiles-under,$(LOCAL_PATH))


