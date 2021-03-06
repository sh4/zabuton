#!/bin/bash

export MAKE_JOB_COUNT=`grep processor /proc/cpuinfo | wc -l`
export MAKEFLAGS="-j $MAKE_JOB_COUNT"
export SCRIPT_ROOT="$(dirname "${BASH_SOURCE:-$0}")"
export ZABUTON_ROOT="$(realpath $SCRIPT_ROOT/../)"
export BUILD_ROOT=$ZABUTON_ROOT/build
export ANDROID_NDK_HOME=$BUILD_ROOT/android-ndk-r21b
export ANDROID_NDK_TOOLCHAIN_ROOT=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64
export TARGET_PREFIX=$BUILD_ROOT/root/target
export TARGET_LIBRARY_PREFIX=$BUILD_ROOT/root/target-lib
export TARGET_GCC_LIB_PREFIX=$BUILD_ROOT/root/target-gcc-lib
export NATIVE_PREFIX=$BUILD_ROOT/root/native
export ORIGINAL_PATH=$PATH

fetch_source ()
{
    local archive_root=$BUILD_ROOT/archives
    mkdir -p $archive_root
    cd $archive_root

    case $1 in
    "gmp")
        _fetch_source https://ftp.gnu.org/gnu/gmp/gmp-6.1.2.tar.xz
        ;;
    "isl")
        _fetch_source http://isl.gforge.inria.fr/isl-0.20.tar.xz
        ;;
    "mpfr")
        _fetch_source https://ftp.gnu.org/gnu/mpfr/mpfr-4.1.0.tar.xz
        ;;
    "mpc")
        _fetch_source https://ftp.gnu.org/gnu/mpc/mpc-1.1.0.tar.gz
        ;;
    "binutils")
        _fetch_source https://ftp.gnu.org/gnu/binutils/binutils-2.32.tar.xz
        ;;
    "gcc")
        _fetch_source https://ftp.gnu.org/gnu/gcc/gcc-8.3.0/gcc-8.3.0.tar.xz
        ;;
    "avrlibc")
        _fetch_source https://download-mirror.savannah.gnu.org/releases/avr-libc/avr-libc-2.0.0.tar.bz2
        ;;
    "busybox")
        _fetch_source https://www.busybox.net/downloads/busybox-1.30.1.tar.bz2
        ;;
    "openssl")
        _fetch_source https://www.openssl.org/source/openssl-1.1.1b.tar.gz
        ;;
    "bash")
        _fetch_source https://ftp.gnu.org/gnu/bash/bash-5.0.tar.gz
        [ ! -d bash-5.0-patches ] && {
            local cwd=`pwd`
            mkdir bash-5.0-patches && cd bash-5.0-patches && \
            curl -O 'https://ftp.gnu.org/gnu/bash/bash-5.0-patches/bash50-[001-007]' || exit $?
        }
        cd $BUILD_BASH_ROOT
        for file in $BUILD_BASH_ROOT/../bash-5.0-patches/*; do
            patch -N -r - -p0 -i $file
        done
        cd $cwd
        ;;
    "libgit2")
        _fetch_source https://codeload.github.com/libgit2/libgit2/tar.gz/v0.28.1 libgit2-0.28.1.tar.gz
        ;;
    "libiconv")
        _fetch_source https://ftp.gnu.org/pub/gnu/libiconv/libiconv-1.15.tar.gz
        ;;
    "curl")
        _fetch_source https://curl.haxx.se/download/curl-7.64.1.tar.gz
        local cacert=$TARGET_LIBRARY_PREFIX/ssl/cacert.pem
        [ ! -d $TARGET_LIBRARY_PREFIX/ssl ] && mkdir -p $TARGET_LIBRARY_PREFIX/ssl
        [ -f $cacert ] || curl https://curl.haxx.se/ca/cacert.pem -o $TARGET_LIBRARY_PREFIX/ssl/cacert.pem || exit $?
        ;;
    "ncurses")
        _fetch_source https://ftp.gnu.org/gnu/ncurses/ncurses-6.1.tar.gz
        ;;
    *)
        echo "Cannot recognized fetch source name: $1" >&2 && exit 1
    esac
}

clean ()
{
	echo ============================================================
    echo [CLEAN] $2 \($1\)
    echo ============================================================
    local cwd=`pwd`
    local tool_root=$BUILD_ROOT/work/$1/$2
    case "$2" in
    "busybox")
        fetch_source "busybox"
        cd $BUILD_BUSYBOX_ROOT
        make clean -j $MAKE_JOB_COUNT
        cd $cwd
        return 0
        ;;
    "avrdude")
        cd $BUILD_ROOT/../externals/avrdude
        if [ -f Makefile ]; then
            make clean
        else
            git clean -xdf .
        fi
        cd $cwd
        ;;
    "vim")
        cd $BUILD_ROOT/../externals/vim
        if [ -f Makefile ]; then
            make clean
        else
            git clean -xdf .
        fi
        cd $cwd
        ;;
    esac
    [ -d $tool_root ] && rm -rf $tool_root && echo Removed: $tool_root
}

build ()
{
    if [ "$CLEAN" = "true" ]; then
        clean $1 $2
    fi
	echo ============================================================
    echo [BUILD] $2 \($1\)
    echo ============================================================
    case "$2" in
    avrdude)
        ;;
    make)
        ;;
    vim)
        ;;
    *)
        fetch_source $2
    esac
    cd "$BUILD_ROOT/work/$1"
    [ ! -d "$2" ] && mkdir "$2"
    cd "$2"
    build_$1_$2
    cd ..
}

_fetch_source ()
{
    local fetch_url=$1
    local source_file=$2
    local curl_options=-O

    if [ "$source_file" = "" ]; then
        source_file=${fetch_url##*/}
    else
        curl_options="-o $source_file"
    fi

    [ -e $source_file ] || curl $curl_options $fetch_url
    [ -e $source_file ] || { echo "Not found: $source_file" >&2; exit 1; }
    local tar_option=""
    case "${source_file##*.}" in
    gz) tar_option=zxvf; ;;
    bz2) tar_option=jxvf; ;;
    xz) tar_option=Jxvf; ;;
    *)
        echo "Cannot recognized fetch source compression type: $source_file" >&2 && exit 1
    esac
    local source_name=${source_file%.tar.*}
    [ -d $source_name ] || tar $tar_option $source_file
    local source_name_without_ver=$(echo $source_name | sed -r -e 's/\.tar\..+$//g' -e 's/-[0-9](\.[0-9a-z]+)*$//g' -e s/-//g)
    local build_root_name="BUILD_${source_name_without_ver^^}_ROOT"
    echo "$build_root_name=$archive_root/$source_name"
    declare -gx "$build_root_name=$archive_root/$source_name"
}

mkdir -p $NATIVE_PREFIX $BUILD_ROOT/work/native
mkdir -p $TARGET_PREFIX $TARGET_LIBRARY_PREFIX $TARGET_GCC_LIB_PREFIX $BUILD_ROOT/work/target

. $SCRIPT_ROOT/build_toolchain_native.sh
. $SCRIPT_ROOT/build_toolchain_target.sh

if [ "$#" -ne 3 ]; then
    echo "AVR GCC toolchain for Android Build utility script"
    echo ""
    echo "Usage: $0 [bulid|clean] [native|target] [tool]"
    echo ""
    echo "  Available tools:"
    echo "    (GCC related tools) gcc, gmp, mpfr, mpc, isl, binutils, avrlibc"
    echo "    (Shell utlis) busybox, make, bash"
    echo "    (Libraries) avrdude, openssl, curl, libgit2, libiconv"
    exit 1
fi

# Usage:
# $1 = build | clean
# $2 = target | native
# $3 = [tool] 
$1 $2 $3