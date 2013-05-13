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
javac -d $(LOCAL_OUT_DIR) -s $(LOCAL_PATH)/java -cp $(LOCAL_OUT_DIR) $(LOCAL_SRC_FILES)
endef

LOCAL_PATH := $(call my-dir)
LOCAL_OUT_DIR := $(LOCAL_PATH)/bin/classes
LOCAL_SRC_FILES := $(call all-java-files-under, java)

all: $(LOCAL_SRC_FILES) $(LOCAL_OUT_DIR)
	$(call build-java-archive)

$(LOCAL_OUT_DIR):
	mkdir -p $(LOCAL_OUT_DIR)

test:
	cd bin/classes && java test.TestBox

