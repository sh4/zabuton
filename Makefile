BUILD_TOOLCHAIN := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))resources/build_toolchain.sh
BUILD_COMMAND = \
	@bash -c "source $(BUILD_TOOLCHAIN) && build $(patsubst target_%,target %,$(patsubst native_%,native %,$@))"
CLEAN_COMMAND = \
	@bash -c "source $(BUILD_TOOLCHAIN) && clean $(patsubst target_%,target %,$(patsubst native_%,native %,$@))"

all: native_% target_%

native_gcc: native_gmp native_mpfr native_mpc native_isl
	$(BUILD_COMMAND)
native_gmp:
	$(BUILD_COMMAND)
native_mpfr: native_gmp 
	$(BUILD_COMMAND)
native_mpc: native_gmp
	$(BUILD_COMMAND)
native_isl: native_gmp
	$(BUILD_COMMAND)
native_binutils:
	$(BUILD_COMMAND)

target_gcc: target_gmp target_mpfr target_mpc target_isl
	$(BUILD_COMMAND)
target_gmp:
	$(BUILD_COMMAND)
target_mpfr: target_gmp 
	$(BUILD_COMMAND)
target_mpc: target_gmp
	$(BUILD_COMMAND)
target_isl: target_gmp
	$(BUILD_COMMAND)
target_binutils:
	$(BUILD_COMMAND)
target_avrlibc: target_gcc target_binutils
	$(BUILD_COMMAND)

target_busybox:
	$(BUILD_COMMAND)
target_make:
	$(BUILD_COMMAND)
target_bash:
	$(BUILD_COMMAND)
target_avrdude:
	$(BUILD_COMMAND)
target_openssl:
	$(BUILD_COMMAND)
target_curl: target_openssl
	$(BUILD_COMMAND)
target_libgit2: target_openssl target_libiconv
	$(BUILD_COMMAND)
target_libiconv:
	$(BUILD_COMMAND)
