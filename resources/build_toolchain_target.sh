#!/bin/sh

target_host=aarch64-linux-android
target_cflags="$CFLAGS -fPIE -fPIC"
target_cxxflags="$CXXFLAGS -fPIE -fPIC -static-libstdc++"
target_path=$ANDROID_NDK_TOOLCHAIN_ROOT/bin:$PATH
zabuton_assets_root=$ZABUTON_ROOT/sources/app/src/main/assets/build

build_target_busybox ()
{
    local cwd=`pwd`
    local cross_compile_prefix="$ANDROID_NDK_TOOLCHAIN_ROOT/bin/aarch64-linux-android"

    cd $BUILD_BUSYBOX_ROOT
    cp -pf $SCRIPT_ROOT/busybox-1.30.1.config $BUILD_BUSYBOX_ROOT/.config
    patch -uN < $SCRIPT_ROOT/0001-busybox-1.30.1-makefile.patch
    CC=aarch64-linux-android22-clang \
    PATH=$target_path \
    CFLAGS="$target_cflags -I$ANDROID_NDK_HOME/sysroot/usr/include/aarch64-linux-android -I$ANDROID_NDK_HOME/sysroot/usr/include" \
    CXXFLAGS="$target_cxxflags" \
    make ARCH=aarch64 CROSS_COMPILE="$cross_compile_prefix-" -j $MAKE_JOB_COUNT && \
    cp -f ./busybox $TARGET_PREFIX/bin/busybox
    cd $cwd
}

build_target_make ()
{
    local cwd=`pwd`
    local make_root=$SCRIPT_ROOT/../externals/make
    cd $make_root
    { [ -f configure ] || ./bootstrap --skip-po || exit $?; } && \
    cd $cwd \
    && \
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags -static" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $make_root/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --disable-posix-spawn \
        --disable-nls \
        --disable-load \
        --with-guile=no \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT &&
    cp -f ./make $TARGET_PREFIX/bin/make 
}

build_target_bash ()
{
    [ -e $BUILD_BASH_ROOT/configure ] || { echo "Not found: $BUILD_BASH_ROOT/configure" >&2; exit 1; }
    local cwd=`pwd`
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags -static" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_BASH_ROOT/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --disable-nls \
        --enable-static-link \
        --without-bash-malloc \
        --enable-largefile \
        --enable-alias \
        --enable-history \
        --enable-readline \
        --enable-multibyte \
        --enable-job-control \
        --enable-array-variables \
        bash_cv_dev_fd=whacky \
        bash_cv_getcwd_malloc=yes \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT &&
    cp -f ./bash $TARGET_PREFIX/bin/bash
}

build_target_openssl ()
{
    [ -e $BUILD_OPENSSL_ROOT/configure ] || { echo "Not found: $BUILD_OPENSSL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_OPENSSL_ROOT/Configure android-arm64 \
        threads zlib-dynamic no-shared no-asm no-sse2 \
        --prefix=$TARGET_LIBRARY_PREFIX \
        -fPIE \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install
}

build_target_libgit2 ()
{
    local cc=$ANDROID_NDK_TOOLCHAIN_ROOT/bin/aarch64-linux-android22-clang
    # TODO: ssh support (libssh2)
    PATH=$target_path \
    # for debug: -DCMAKE_VERBOSE_MAKEFILE=ON
    cmake $BUILD_LIBGIT2_ROOT \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_CLAR=OFF \
        -DUSE_ICONV=ON \
        -DCMAKE_SYSTEM_NAME=Linux \
        -DCMAKE_SYSTEM_VERSION=Android \
        -DCMAKE_AR=$ANDROID_NDK_TOOLCHAIN_ROOT/bin/aarch64-linux-android-ar \
        -DCMAKE_C_COMPILER=$cc \
        -DCMAKE_CXX_COMPILER=$cc++ \
        -DCMAKE_FIND_ROOT_PATH=$ANDROID_NDK_TOOLCHAIN_ROOT/sysroot \
        -DOPENSSL_ROOT_DIR=$TARGET_LIBRARY_PREFIX \
        -DCMAKE_INSTALL_PREFIX=$TARGET_LIBRARY_PREFIX \
    && \
    PATH=$target_path \
    cmake --build . -- -j $MAKE_JOB_COUNT &&
    PATH=$target_path \
    cmake --build . --target install
}

build_target_libiconv ()
{
    [ -e $BUILD_LIBICONV_ROOT/configure ] || { echo "Not found: $BUILD_LIBICONV_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_LIBICONV_ROOT/configure \
        --prefix=$TARGET_LIBRARY_PREFIX \
        --host=$target_host \
        --enable-static \
        --enable-shared=no \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install
}

build_target_curl ()
{
    [ -e $BUILD_CURL_ROOT/configure ] || { echo "Not found: $BUILD_CURL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    LIBS="-lc -lz -ldl" \
    CFLAGS="$target_cflags" \
    CPPFLAGS="$CFLAGS" \
    CXXFLAGS="$target_cxxflags" \
    LDFLAGS="$LDFLAGS -L$TARGET_LIBRARY_PREFIX/lib" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_CURL_ROOT/configure \
        --prefix=$TARGET_LIBRARY_PREFIX \
        --host=$target_host \
        --with-ssl=$TARGET_LIBRARY_PREFIX \
        --with-ca-bundle=$TARGET_LIBRARY_PREFIX/ssl/cacert.pem \
        --without-libidn \
        --without-librtmp \
        --enable-static \
        --disable-shared \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install
}

build_target_avrdude ()
{
    local cwd=`pwd`
    local avrdude_root=$SCRIPT_ROOT/../externals/avrdude
    { [ -f $avrdude_root/configure ] || (cd $avrdude_root && ./bootstrap) || exit $?; }
    cd $cwd
    { [ -f Makefile ] || \
        PATH=$target_path \
        CFLAGS="$target_cflags" \
        CC=aarch64-linux-android22-clang \
        $avrdude_root/configure \
            --host=$target_host \
            --prefix=$TARGET_LIBRARY_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install && \
    cp -f ./ac_cfg.h $avrdude_root/ && \
    mkdir -p $zabuton_assets_root && \
    cp -f $TARGET_LIBRARY_PREFIX/etc/avrdude.conf $zabuton_assets_root/
}

build_target_pigz ()
{
    local cwd=`pwd`
    cd $BUILD_PIGZ_ROOT
    patch -p1 -uN < $SCRIPT_ROOT/0001-pigz-2.4-android.patch
    PATH=$target_path make clean && \
    PATH=$target_path make \
        CC=aarch64-linux-android22-clang \
        CFLAGS="$target_cflags -I$ANDROID_NDK_HOME/sysroot/usr/include" \
        LIBS="-lm -lz" \
        -j $MAKE_JOB_COUNT && \
    mkdir -p $zabuton_assets_root && \
    cp -pf ./pigz $zabuton_assets_root/
    cd $cwd
}

build_target_gmp ()
{
    [ -e $BUILD_GMP_ROOT/configure ] || { echo "Not found: $BUILD_GMP_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_GMP_ROOT/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --enable-cxx \
        --with-pic \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install
}

build_target_isl ()
{
    [ -e $BUILD_ISL_ROOT/configure ] || { echo "Not found: $BUILD_ISL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    LDFLAGS="$LDFLAGS -L$TARGET_PREFIX/lib -lgmp" $BUILD_ISL_ROOT/configure \
        --host=$target_host \
        --prefix=$TARGET_PREFIX \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp-prefix=$TARGET_PREFIX \
    || exit $?; } && \
    PATH=$target_path LDFLAGS="$LDFLAGS -L$TARGET_PREFIX/lib -lgmp" make -j $MAKE_JOB_COUNT && \
    PATH=$target_path LDFLAGS="$LDFLAGS -L$TARGET_PREFIX/lib -lgmp" make install -j $MAKE_JOB_COUNT
}

build_target_mpfr ()
{
    [ -e $BUILD_MPFR_ROOT/configure ] || { echo "Not found: $BUILD_MPFR_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path  \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_MPFR_ROOT/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp=$TARGET_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT
}

build_target_mpc ()
{
    [ -e $BUILD_MPC_ROOT/configure ] || { echo "Not found: $BUILD_MPC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path  \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_MPC_ROOT/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --disable-dependency-tracking \
        --with-gmp=$TARGET_PREFIX \
        --with-mpfr=$TARGET_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT
}

build_target_binutils ()
{
    local cwd=`pwd`
    [ -e $BUILD_BINUTILS_ROOT/configure ] || { echo "Not found: $BUILD_BINUTILS_ROOT/configure" >&2; exit 1; }
    cd $BUILD_BINUTILS_ROOT
    patch -p1 -uN < $SCRIPT_ROOT/0001-binutils-2.32-android.patch
    cd $cwd
    { [ -f Makefile ] || \
    PATH=$target_path  \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    $BUILD_BINUTILS_ROOT/configure \
        --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --target=avr \
        --disable-nls \
        --disable-werror \
        --with-gmp=$TARGET_PREFIX \
        --with-mpc=$TARGET_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT
}

build_target_gcc ()
{
    local bigendian=no
    local gcc_target_path=$NATIVE_PREFIX/bin:$target_path

    [ -e $BUILD_GCC_ROOT/configure ] || { echo "Not found: $BUILD_GCC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$gcc_target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=aarch64-linux-android22-clang \
    CXX=aarch64-linux-android22-clang++ \
    CC_FOR_TARGET=avr-gcc \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    gcc_cv_prog_makeinfo_modern=no \
    gcc_cv_no_pie=no \
    gcc_cv_c_no_fpie=no \
    ac_cv_c_bigendian=$bigendian \
    $BUILD_GCC_ROOT/configure \
        --host=$target_host \
        --target=avr \
        --prefix=$TARGET_PREFIX \
        --enable-languages=c,c++ \
        --disable-nls \
        --disable-libssp \
        --disable-shared \
        --disable-bootstrap \
        --disable-threads \
        --disable-libgomp \
        --disable-lto \
        --disable-plugin \
        --with-dwarf2 \
        --with-gmp=$TARGET_PREFIX \
        --with-mpc=$TARGET_PREFIX \
        --with-mpfr=$TARGET_PREFIX \
        --with-ld=$NATIVE_PREFIX/bin/avr-ld \
        --with-as=$NATIVE_PREFIX/bin/avr-as \
        --without-headers \
    || exit $?; } && \
    PATH=$gcc_target_path \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    ac_cv_c_bigendian=$bigendian \
    make -j $MAKE_JOB_COUNT && \
    PATH=$gcc_target_path \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    make install -j $MAKE_JOB_COUNT
}

build_target_avrlibc ()
{
    local avrpath=$NATIVE_PREFIX/bin:$ORIGINAL_PATH
    [ -e $BUILD_AVRLIBC_ROOT/configure ] || { echo "Not found: $BUILD_AVRLIBC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    PATH=$avrpath \
    AR=avr-ar \
    AS=avr-as \
    CC=avr-gcc \
    CXX=avr-g++ \
    LD=avr-ld \
    STRIP=avr-strip \
    $BUILD_AVRLIBC_ROOT/configure \
        --prefix=$TARGET_PREFIX \
        --host=avr \
    || exit $?; } && \
    PATH=$avrpath make -j $MAKE_JOB_COUNT && \
    PATH=$avrpath make install -j $MAKE_JOB_COUNT
}
