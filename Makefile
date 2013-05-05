define my-dir
$(strip \
		$(eval LOCAL_MODULE_MAKEFILE := $$(lastword $$(MAKEFILE_LIST))) \
		$(if $(filter $(CLEAR_VARS),$(LOCAL_MODULE_MAKEFILE)), \
			$(error LOCAL_PATH must be set before including $$(CLEAR_VARS)) \
			, \
			$(patsubst %/,%,$(dir $(LOCAL_MODULE_MAKEFILE))) \
		 ) \
 )
endef

define all-java-files-under
$(patsubst ./%,%, \
		$(shell cd $(LOCAL_PATH) ; \
			find $(1) -name "*.java" -and -not -name ".*") \
 )
endef

define build-java-archive
javac -d $(LOCAL_OUT_DIR) -s $(LOCAL_PATH)/src -cp $(LOCAL_OUT_DIR) $(LOCAL_SRC_FILES)
endef

LOCAL_PATH := $(call my-dir)
LOCAL_OUT_DIR := $(LOCAL_PATH)/out
LOCAL_SRC_FILES := $(call all-java-files-under, src)

all: $(LOCAL_SRC_FILES) $(LOCAL_OUT_DIR)
	$(call build-java-archive)

$(LOCAL_OUT_DIR):
	mkdir $(LOCAL_OUT_DIR)

test:
	cd out && java test.TestBox

