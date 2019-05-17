#!/bin/bash

target_host=aarch64-linux-android
target_cc=aarch64-linux-android23-clang
target_cxx=aarch64-linux-android23-clang++
target_cflags="-fPIE -fPIC"
target_cxxflags="-fPIE -fPIC -static-libstdc++"
target_path=$ANDROID_NDK_TOOLCHAIN_ROOT/bin:$PATH
target_strip=$ANDROID_NDK_TOOLCHAIN_ROOT/bin/aarch64-linux-android-strip

zabuton_assets_root=$ZABUTON_ROOT/sources/app/src/main/assets/build

build_target_busybox ()
{
    local cwd=`pwd`
    local cross_compile_prefix="$ANDROID_NDK_TOOLCHAIN_ROOT/bin/aarch64-linux-android"

    cd $BUILD_BUSYBOX_ROOT
    cp -pf $SCRIPT_ROOT/busybox-1.30.1.config $BUILD_BUSYBOX_ROOT/.config
    patch -uN < $SCRIPT_ROOT/0001-busybox-1.30.1-makefile.patch
    CC=$target_cc \
    PATH=$target_path \
    CFLAGS="$target_cflags -I$ANDROID_NDK_HOME/sysroot/usr/include/aarch64-linux-android -I$ANDROID_NDK_HOME/sysroot/usr/include" \
    CXXFLAGS="$target_cxxflags" \
    make ARCH=aarch64 CROSS_COMPILE="$cross_compile_prefix-" -j $MAKE_JOB_COUNT && \
    cp -f ./busybox $zabuton_assets_root/ && \
    $target_strip $zabuton_assets_root/busybox && \
    cd $cwd \
    || exit $?
}

build_target_make ()
{
    local cwd=`pwd`
    local make_root=$SCRIPT_ROOT/../externals/make
    cd $make_root
    { [ -f configure ] || PATH=$target_path ./bootstrap --skip-po || exit $?; } && \
    cd $cwd \
    && \
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags -static" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $make_root/configure --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --disable-posix-spawn \
        --disable-nls \
        --disable-load \
        --with-guile=no \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT &&
    cp -f ./make $TARGET_PREFIX/bin/make && \
    $target_strip $TARGET_PREFIX/bin/make \
    || exit $?
}

build_target_bash ()
{
    local cwd=`pwd`
    [ -e $BUILD_BASH_ROOT/configure ] || { echo "Not found: $BUILD_BASH_ROOT/configure" >&2; exit 1; }
    cd $BUILD_BASH_ROOT
    patch -N -r - -p1 < $SCRIPT_ROOT/0001-bash-5.0-android.patch
    autoconf
    cd $cwd
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags -static" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
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
    LOCAL_LDFLAGS= PATH=$target_path make -j $MAKE_JOB_COUNT &&
    cp -f ./bash $TARGET_PREFIX/bin/bash && \
    $target_strip $TARGET_PREFIX/bin/bash \
    || exit $?
}

build_target_openssl ()
{
    [ -e $BUILD_OPENSSL_ROOT/configure ] || { echo "Not found: $BUILD_OPENSSL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_OPENSSL_ROOT/Configure android-arm64 \
        threads zlib-dynamic no-rc4 no-stdio no-tests no-shared no-asm no-sse2 \
        --prefix=$TARGET_LIBRARY_PREFIX \
        -fPIE \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_target_libgit2 ()
{
    local cc=$ANDROID_NDK_TOOLCHAIN_ROOT/bin/$target_cc
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
    cmake --build . --target install \
    || exit $?
}

build_target_libiconv ()
{
    [ -e $BUILD_LIBICONV_ROOT/configure ] || { echo "Not found: $BUILD_LIBICONV_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_LIBICONV_ROOT/configure \
        --prefix=$TARGET_LIBRARY_PREFIX \
        --host=$target_host \
        --enable-static \
        --enable-shared=no \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install \
    || exit $?
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
    CC=$target_cc \
    CXX=$target_cxx \
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
    PATH=$target_path make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_target_avrdude ()
{
    local cwd=`pwd`
    local avrdude_root=$SCRIPT_ROOT/../externals/avrdude
    { [ -f $avrdude_root/configure ] || (cd $avrdude_root && PATH=$target_path ./bootstrap) || exit $?; }
    cd $cwd
    { [ -f Makefile ] || \
        PATH=$target_path \
        CFLAGS="$target_cflags" \
        CC=$target_cc \
        $avrdude_root/configure \
            --host=$target_host \
            --prefix=$TARGET_LIBRARY_PREFIX \
            --disable-shared \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make -j $MAKE_JOB_COUNT install && \
    cp -f ./ac_cfg.h $avrdude_root/ && \
    mkdir -p $zabuton_assets_root && \
    cp -f $TARGET_LIBRARY_PREFIX/etc/avrdude.conf $zabuton_assets_root/ \
    || exit $?
}

build_target_pigz ()
{
    local cwd=`pwd`
    cd $BUILD_PIGZ_ROOT
    patch -p1 -uN < $SCRIPT_ROOT/0001-pigz-2.4-android.patch
    PATH=$target_path make clean && \
    PATH=$target_path make \
        CC=$target_cc \
        CFLAGS="$target_cflags -I$ANDROID_NDK_HOME/sysroot/usr/include" \
        LIBS="-lm -lz" \
        -j $MAKE_JOB_COUNT && \
    mkdir -p $zabuton_assets_root && \
    cp -pf ./pigz $zabuton_assets_root/ && \
    cd $cwd \
    || exit $?   
}

build_target_gmp ()
{
    [ -e $BUILD_GMP_ROOT/configure ] || { echo "Not found: $BUILD_GMP_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_GMP_ROOT/configure --prefix=$TARGET_GCC_LIB_PREFIX \
        --host=$target_host \
        --enable-cxx \
        --with-pic \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install \
    || exit $?
}

build_target_isl ()
{
    [ -e $BUILD_ISL_ROOT/configure ] || { echo "Not found: $BUILD_ISL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    LDFLAGS="$LDFLAGS -L$TARGET_GCC_LIB_PREFIX/lib -lgmp" $BUILD_ISL_ROOT/configure \
        --host=$target_host \
        --prefix=$TARGET_GCC_LIB_PREFIX \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp-prefix=$TARGET_GCC_LIB_PREFIX \
    || exit $?; } && \
    PATH=$target_path LDFLAGS="$LDFLAGS -L$TARGET_GCC_LIB_PREFIX/lib -lgmp" make -j $MAKE_JOB_COUNT && \
    PATH=$target_path LDFLAGS="$LDFLAGS -L$TARGET_GCC_LIB_PREFIX/lib -lgmp" make install -j $MAKE_JOB_COUNT \
    || exit $?
}

build_target_mpfr ()
{
    [ -e $BUILD_MPFR_ROOT/configure ] || { echo "Not found: $BUILD_MPFR_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path  \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_MPFR_ROOT/configure --prefix=$TARGET_GCC_LIB_PREFIX \
        --host=$target_host \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp=$TARGET_GCC_LIB_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT \
    || exit $?
}

build_target_mpc ()
{
    [ -e $BUILD_MPC_ROOT/configure ] || { echo "Not found: $BUILD_MPC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$target_path  \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_MPC_ROOT/configure --prefix=$TARGET_GCC_LIB_PREFIX \
        --host=$target_host \
        --disable-dependency-tracking \
        --with-gmp=$TARGET_GCC_LIB_PREFIX \
        --with-mpfr=$TARGET_GCC_LIB_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT \
    || exit $?
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
    CC=$target_cc \
    CXX=$target_cxx \
    $BUILD_BINUTILS_ROOT/configure \
        --prefix=$TARGET_PREFIX \
        --host=$target_host \
        --target=avr \
        --disable-nls \
        --disable-werror \
        --with-gmp=$TARGET_GCC_LIB_PREFIX \
        --with-mpc=$TARGET_GCC_LIB_PREFIX \
    || exit $?; } && \
    PATH=$target_path make -j $MAKE_JOB_COUNT && \
    PATH=$target_path make install -j $MAKE_JOB_COUNT \
    || exit $?
    $target_strip $TARGET_PREFIX/avr/bin/* $TARGET_PREFIX/bin/*
}

build_target_gcc ()
{
    local gcc_target_path=$NATIVE_PREFIX/bin:$target_path

    export gcc_cv_prog_makeinfo_modern=no
    export gcc_cv_no_pie=no
    export gcc_cv_c_no_fpie=no
    export ac_cv_c_bigendian=no

    [ -e $BUILD_GCC_ROOT/configure ] || { echo "Not found: $BUILD_GCC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    PATH=$gcc_target_path \
    CFLAGS="$target_cflags" \
    CXXFLAGS="$target_cxxflags" \
    CC=$target_cc \
    CXX=$target_cxx \
    CC_FOR_TARGET=avr-gcc \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
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
        --with-gmp=$TARGET_GCC_LIB_PREFIX \
        --with-mpc=$TARGET_GCC_LIB_PREFIX \
        --with-mpfr=$TARGET_GCC_LIB_PREFIX \
        --with-ld=$NATIVE_PREFIX/bin/avr-ld \
        --with-as=$NATIVE_PREFIX/bin/avr-as \
        --without-headers \
    || exit $?; } && \
    PATH=$gcc_target_path \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    make -j $MAKE_JOB_COUNT && \
    PATH=$gcc_target_path \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    make install -j $MAKE_JOB_COUNT && \
    cp -f $TARGET_GCC_LIB_PREFIX/lib/*.so $TARGET_PREFIX/lib \
    || exit $?
    $target_strip $TARGET_PREFIX/bin/* $TARGET_PREFIX/libexec/gcc/avr/8.3.0/*
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
    PATH=$avrpath make install -j $MAKE_JOB_COUNT \
    || exit $?
}
