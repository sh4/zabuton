# make
# make lib NAME=gcc BUILD_AREA=native

BUILD_ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
BUILD_TOOLCHAIN := $(BUILD_ROOT)resources/build_toolchain.sh
BUILD_COMMAND := @CLEAN=$(CLEAN) $(BUILD_TOOLCHAIN) build
CLEAN_COMMAND := @$(BUILD_TOOLCHAIN) clean
ZABUTON_ASSETS_ROOT := $(BUILD_ROOT)sources/app/src/main/assets/build
TARGET_ROOT := $(BUILD_ROOT)build/root/target
NATIVE_ROOT := $(BUILD_ROOT)build/root/native
TARGET_LIB_ROOT := $(BUILD_ROOT)build/root/target-lib
TARGET_TOOLS := $(TARGET_ROOT)/bin/avr-gcc \
	$(TARGET_ROOT)/avr/lib/libc.a \
	$(TARGET_ROOT)/avr/bin/ar \
	$(TARGET_ROOT)/bin/busybox \
	$(TARGET_ROOT)/bin/make \
	$(TARGET_ROOT)/bin/bash \
	$(TARGET_ROOT)/bin/vim
TARGET_LIBS := $(ZABUTON_ASSETS_ROOT)/pigz \
	$(TARGET_LIB_ROOT)/lib/libcurl.a \
	$(TARGET_LIB_ROOT)/lib/libgit2.a \
	$(TARGET_LIB_ROOT)/lib/libavrdude.a
NATIVE_GCC_LIBS := $(NATIVE_ROOT)/lib/libgmp.a \
	$(NATIVE_ROOT)/lib/libmpfr.a \
	$(NATIVE_ROOT)/lib/libmpc.a \
	$(NATIVE_ROOT)/lib/libisl.a
TARGET_GCC_LIBS := 	$(NATIVE_ROOT)/bin/avr-gcc \
	$(TARGET_ROOT)/lib/libgmp.a \
	$(TARGET_ROOT)/lib/libmpfr.a \
	$(TARGET_ROOT)/lib/libmpc.a \
	$(TARGET_ROOT)/lib/libisl.a

CLEAN := false
BUILD_AREA := target # or "native"
NAME :=

all: $(ZABUTON_ASSETS_ROOT)/avr-gcc.tar.gz $(TARGET_LIBS)
all-target-tools: $(TARGET_TOOLS)
all-target-libs: $(TARGET_LIBS)
lib:
	$(BUILD_COMMAND) $(BUILD_AREA) $(NAME)

$(ZABUTON_ASSETS_ROOT)/avr-gcc.tar.gz: $(TARGET_TOOLS)
	cd $(BULID_ROOT)build/root/target && \
	tar czvf $(ZABUTON_ASSETS_ROOT)/avr-gcc.tar.gz \
		--exclude=share/doc \
		--exclude=share/info \
		--exclude=share/man \
		--exclude=share/vim/vim81/doc \
		--exclude=share/vim/vim81/tutor \
		* --hard-dereference
$(ZABUTON_ASSETS_ROOT)/pigz:
	$(BUILD_COMMAND) target pigz
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

$(TARGET_ROOT)/bin/avr-gcc: $(TARGET_GCC_LIBS)
	$(BUILD_COMMAND) target gcc
$(TARGET_ROOT)/lib/libgmp.a:
	$(BUILD_COMMAND) target gmp
$(TARGET_ROOT)/lib/libmpfr.a: $(TARGET_ROOT)/lib/libgmp.a 
	$(BUILD_COMMAND) target mpfr
$(TARGET_ROOT)/lib/libmpc.a: $(TARGET_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) target mpc
$(TARGET_ROOT)/lib/libisl.a: $(TARGET_ROOT)/lib/libgmp.a
	$(BUILD_COMMAND) target isl
$(TARGET_ROOT)/avr/bin/ar:
	$(BUILD_COMMAND) target binutils
$(TARGET_ROOT)/avr/lib/libc.a: $(TARGET_ROOT)/bin/avr-gcc $(TARGET_ROOT)/avr/bin/ar
	$(BUILD_COMMAND) target avrlibc
$(TARGET_ROOT)/bin/busybox: 
	$(BUILD_COMMAND) target busybox
$(TARGET_ROOT)/bin/make: $(wildcard $(BUILD_ROOT)externals/make/src/*.c $(BUILD_ROOT)externals/make/src/*.h)
	$(BUILD_COMMAND) target make
$(TARGET_ROOT)/bin/bash:
	$(BUILD_COMMAND) target bash
$(TARGET_ROOT)/bin/vim: $(TARGET_LIB_ROOT)/lib/libncurses.a
	$(BUILD_COMMAND) target vim
$(TARGET_LIB_ROOT)/lib/libncurses.a:
	$(BUILD_COMMAND) target ncurses

AVRDUDE_DEPS := $(filter-out $(BUILD_ROOT)externals/avrdude/ac_cfg.h, \
	$(wildcard \
		$(BUILD_ROOT)externals/avrdude/*.c \
		$(BUILD_ROOT)externals/avrdude/*.h))

$(TARGET_LIB_ROOT)/lib/libavrdude.a: $(AVRDUDE_DEPS)
	$(BUILD_COMMAND) target avrdude
$(TARGET_LIB_ROOT)/lib/libssl.a:
	$(BUILD_COMMAND) target openssl
$(TARGET_LIB_ROOT)/lib/libcurl.a: $(TARGET_LIB_ROOT)/lib/libssl.a
	$(BUILD_COMMAND) target curl
$(TARGET_LIB_ROOT)/lib/libgit2.a: $(TARGET_LIB_ROOT)/lib/libssl.a $(TARGET_LIB_ROOT)/lib/libiconv.a
	$(BUILD_COMMAND) target libgit2
$(TARGET_LIB_ROOT)/lib/libiconv.a:
	$(BUILD_COMMAND) target libiconv
