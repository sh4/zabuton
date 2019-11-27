ZABUTON_ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
BUILD_ROOT := $(ZABUTON_ROOT)build/
BUILD_TOOLCHAIN := $(ZABUTON_ROOT)resources/build_toolchain.sh
BUILD_COMMAND := @CLEAN=$(CLEAN) $(BUILD_TOOLCHAIN) build
ZABUTON_ASSETS_ROOT := $(ZABUTON_ROOT)sources/app/src/main/assets/build
TARGET_ROOT := $(BUILD_ROOT)root/target
NATIVE_ROOT := $(BUILD_ROOT)root/native
WORK_TARGET_ROOT := $(BUILD_ROOT)work/target
WORK_NATIVE_ROOT := $(BUILD_ROOT)work/native
TARGET_LIB_ROOT := $(BUILD_ROOT)root/target-lib
TARGET_GCC_LIB_ROOT := $(BUILD_ROOT)root/target-gcc-lib
TARGET_TOOLS := \
	$(TARGET_ROOT)/bin/avr-gcc \
	$(TARGET_ROOT)/avr/lib/libc.a \
	$(TARGET_ROOT)/avr/bin/ar \
	$(TARGET_ROOT)/bin/make \
	$(TARGET_ROOT)/bin/bash \
	$(TARGET_ROOT)/bin/vim \
	$(TARGET_ROOT)/bin/avrdude \
	$(TARGET_ROOT)/bin/busybox
TARGET_LIBS := \
	$(TARGET_LIB_ROOT)/lib/libcurl.a \
	$(TARGET_LIB_ROOT)/lib/libgit2.a
ZABUTON_ASSETS := \
	$(ZABUTON_ASSETS_ROOT)/toolchain.zip \
	$(ZABUTON_ASSETS_ROOT)/symlinkMaps.json
NATIVE_GCC_LIBS := \
	$(NATIVE_ROOT)/lib/libgmp.a \
	$(NATIVE_ROOT)/lib/libmpfr.a \
	$(NATIVE_ROOT)/lib/libmpc.a \
	$(NATIVE_ROOT)/lib/libisl.a
TARGET_GCC_LIBS := \
	$(NATIVE_ROOT)/bin/avr-gcc \
	$(TARGET_GCC_LIB_ROOT)/lib/libgmp.a \
	$(TARGET_GCC_LIB_ROOT)/lib/libmpfr.a \
	$(TARGET_GCC_LIB_ROOT)/lib/libmpc.a \
	$(TARGET_GCC_LIB_ROOT)/lib/libisl.a
NDK_BUILD := $(BUILD_ROOT)android-ndk-r20/ndk-build

CLEAN := false

.DEFAULT_GOAL := all

.PHONY: all clean
all: $(ZABUTON_ASSETS) $(TARGET_LIBS)
clean:
	@$(BUILD_TOOLCHAIN) clean

.PHONY: gcc make bash vim avrdude libgit2
gcc: $(TARGET_ROOT)/bin/avr-gcc
make: $(TARGET_ROOT)/bin/make
bash: $(TARGET_ROOT)/bin/bash
vim: $(TARGET_ROOT)/bin/vim
busybox: $(TARGET_ROOT)/bin/busybox
avrdude: $(TARGET_ROOT)/bin/avrdude
libgit2: $(TARGET_LIB_ROOT)/lib/libgit2.a

$(ZABUTON_ASSETS_ROOT)/toolchain.zip: $(TARGET_TOOLS)
	cd $(TARGET_ROOT) && \
	rm -f $(ZABUTON_ASSETS_ROOT)/toolchain.zip ; \
	zip -4 -r $(ZABUTON_ASSETS_ROOT)/toolchain.zip \
		--exclude='avr/bin/*' \
		--exclude='share/doc/*' \
		--exclude='share/info/*' \
		--exclude='share/man/*' \
		--exclude='share/vim/vim81/doc/*' \
		--exclude='share/vim/vim81/tutor/*' \
		--exclude='share/vim/vim81/spell/*' \
		*
$(ZABUTON_ASSETS_ROOT)/symlinkMaps.json:
	cp -f $(ZABUTON_ROOT)resources/symlinkMaps.json $@

$(TARGET_ROOT)/bin/busybox: $(NDK_BUILD)
	$(BUILD_COMMAND) target busybox

$(NATIVE_ROOT)/bin/avr-gcc: $(NATIVE_GCC_LIBS) $(NATIVE_ROOT)/avr/bin/ar
	$(BUILD_COMMAND) native gcc
$(NATIVE_ROOT)/lib/libgmp.a:
	$(BUILD_COMMAND) native gmp
$(NATIVE_ROOT)/lib/libmpfr.a: $(NATIVE_ROOT)/lib/libgmp.a 
	$(BUILD_COMMAND) native mpfr
$(NATIVE_ROOT)/lib/libmpc.a: $(NATIVE_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) native mpc
$(NATIVE_ROOT)/lib/libisl.a: $(NATIVE_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) native isl
$(NATIVE_ROOT)/avr/bin/ar:
	$(BUILD_COMMAND) native binutils

$(TARGET_ROOT)/bin/avr-gcc: $(NDK_BUILD) $(TARGET_GCC_LIBS) $(NATIVE_ROOT)/bin/avr-gcc
	$(BUILD_COMMAND) target gcc
$(TARGET_GCC_LIB_ROOT)/lib/libgmp.a: $(NDK_BUILD) 
	$(BUILD_COMMAND) target gmp
$(TARGET_GCC_LIB_ROOT)/lib/libmpfr.a: $(NDK_BUILD) $(TARGET_GCC_LIB_ROOT)/lib/libgmp.a 
	$(BUILD_COMMAND) target mpfr
$(TARGET_GCC_LIB_ROOT)/lib/libmpc.a: $(NDK_BUILD) $(TARGET_GCC_LIB_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) target mpc
$(TARGET_GCC_LIB_ROOT)/lib/libisl.a: $(NDK_BUILD) $(TARGET_GCC_LIB_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) target isl
$(TARGET_ROOT)/avr/bin/ar: $(NDK_BUILD)
	$(BUILD_COMMAND) target binutils
$(TARGET_ROOT)/avr/lib/libc.a: $(NDK_BUILD) $(NATIVE_ROOT)/bin/avr-gcc $(TARGET_ROOT)/avr/bin/ar
	$(BUILD_COMMAND) target avrlibc
$(TARGET_ROOT)/bin/make: $(NDK_BUILD) $(wildcard $(ZABUTON_ROOT)externals/make/src/*.c $(ZABUTON_ROOT)externals/make/src/*.h)
	$(BUILD_COMMAND) target make
$(TARGET_ROOT)/bin/bash: $(NDK_BUILD)
	$(BUILD_COMMAND) target bash
$(TARGET_ROOT)/bin/vim: $(NDK_BUILD) $(TARGET_LIB_ROOT)/lib/libncurses.a
	$(BUILD_COMMAND) target vim
$(TARGET_LIB_ROOT)/lib/libncurses.a: $(NDK_BUILD)
	$(BUILD_COMMAND) target ncurses

AVRDUDE_DEPS := $(filter-out $(ZABUTON_ROOT)externals/avrdude/ac_cfg.h, \
	$(wildcard \
		$(ZABUTON_ROOT)externals/avrdude/*.c \
		$(ZABUTON_ROOT)externals/avrdude/*.cpp \
		$(ZABUTON_ROOT)externals/avrdude/*.h))

$(TARGET_ROOT)/bin/avrdude: $(NDK_BUILD) $(AVRDUDE_DEPS)
	$(BUILD_COMMAND) target avrdude
$(TARGET_LIB_ROOT)/lib/libgit2.a: $(NDK_BUILD) $(TARGET_LIB_ROOT)/lib/libssl.a $(TARGET_LIB_ROOT)/lib/libiconv.a
	$(BUILD_COMMAND) target libgit2
$(TARGET_LIB_ROOT)/lib/libcurl.a: $(NDK_BUILD) $(TARGET_LIB_ROOT)/lib/libssl.a
	$(BUILD_COMMAND) target curl
$(TARGET_LIB_ROOT)/lib/libssl.a: $(NDK_BUILD)
	$(BUILD_COMMAND) target openssl
$(TARGET_LIB_ROOT)/lib/libiconv.a: $(NDK_BUILD)
	$(BUILD_COMMAND) target libiconv

$(NDK_BUILD): $(BUILD_ROOT)android-ndk-r20-linux-x86_64.zip
	unzip -DD -o $< -d $(BUILD_ROOT)