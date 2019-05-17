#!/bin/bash

build_native_gmp ()
{
    [ -e $BUILD_GMP_ROOT/configure ] || { echo "Not found: $BUILD_GMP_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    $BUILD_GMP_ROOT/configure --prefix=$NATIVE_PREFIX \
        --enable-cxx \
        --with-pic \
    || exit $?; } && \
    make -j $MAKE_JOB_COUNT && \
    make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_native_isl ()
{
    [ -e $BUILD_ISL_ROOT/configure ] || { echo "Not found: $BUILD_ISL_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    LDFLAGS="$LDFLAGS -L$NATIVE_PREFIX/lib -lgmp" $BUILD_ISL_ROOT/configure \
        --prefix=$NATIVE_PREFIX \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp-prefix=$NATIVE_PREFIX \
    || exit $?; } && \
    LDFLAGS="$LDFLAGS -L$NATIVE_PREFIX/lib -lgmp" make -j $MAKE_JOB_COUNT && \
    LDFLAGS="$LDFLAGS -L$NATIVE_PREFIX/lib -lgmp" make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_native_mpfr ()
{
    [ -e $BUILD_MPFR_ROOT/configure ] || { echo "Not found: $BUILD_MPFR_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    $BUILD_MPFR_ROOT/configure --prefix=$NATIVE_PREFIX \
        --disable-dependency-tracking \
        --disable-silent-rules \
        --with-gmp=$NATIVE_PREFIX \
    || exit $?; } && \
    make -j $MAKE_JOB_COUNT && \
    make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_native_mpc ()
{
    [ -e $BUILD_MPC_ROOT/configure ] || { echo "Not found: $BUILD_MPC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    $BUILD_MPC_ROOT/configure --prefix=$NATIVE_PREFIX \
        --disable-dependency-tracking \
        --with-gmp=$NATIVE_PREFIX \
        --with-mpfr=$NATIVE_PREFIX \
    || exit $?; } && \
    make -j $MAKE_JOB_COUNT &&
    make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_native_binutils ()
{
    [ -e $BUILD_BINUTILS_ROOT/configure ] || { echo "Not found: $BUILD_BINUTILS_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    $BUILD_BINUTILS_ROOT/configure \
        --prefix=$NATIVE_PREFIX \
        --target=avr \
        --disable-nls \
        --disable-werror \
        --with-gmp=$NATIVE_PREFIX \
        --with-mpc=$NATIVE_PREFIX \
    || exit $?; } && \
    make -j $MAKE_JOB_COUNT && \
    make -j $MAKE_JOB_COUNT install \
    || exit $?
}

build_native_gcc ()
{
    [ -e $BUILD_GCC_ROOT/configure ] || { echo "Not found: $BUILD_GCC_ROOT/configure" >&2; exit 1; }
    { [ -f Makefile ] || \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    gcc_cv_prog_makeinfo_modern=no \
    $BUILD_GCC_ROOT/configure \
        --target=avr \
        --prefix=$NATIVE_PREFIX \
        --exec-prefix=$NATIVE_PREFIX \
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
        --with-gmp=$NATIVE_PREFIX \
        --with-mpc=$NATIVE_PREFIX \
        --with-mpfr=$NATIVE_PREFIX \
        --with-as=$NATIVE_PREFIX/bin/avr-as \
        --with-ld=$NATIVE_PREFIX/bin/avr-ld \
        --without-headers \
    || exit $?; } && \
    PATH=$NATIVE_PREFIX/bin:$PATH \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    make -j $MAKE_JOB_COUNT && \
    PATH=$NATIVE_PREFIX/bin:$PATH \
    LD_LIBRARY_PATH=$NATIVE_PREFIX/lib \
    make -j $MAKE_JOB_COUNT install \
    || exit $?
}