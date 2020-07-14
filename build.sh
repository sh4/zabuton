#!/bin/bash

root="$(realpath $(dirname "${BASH_SOURCE:-$0}"))"
tag=zabuton:latest

usage ()
{
    echo "AVR GCC toolchain for Android Build script"
    echo ""
    echo "Usage: $0 [docker|toolchain]"
    echo ""
    echo "  docker:"
    echo "    Build docker image for AVR GCC toolchain build."
    echo ""
    echo "  toolchain:"
    echo "    Build AVR GCC toolchain on docker."
    echo "" 
}

docker_build_if_not_exists ()
{
    if [ "$(docker image ls zabuton:latest -q)" = "" ]; then
        docker build -t $tag "$root"
        exitcode=$?
        if [ $exitcode -ne 0 ]; then
            exit $exitcode
        fi
    fi
}

if [ "$#" -lt 1 ]; then
    usage
    exit 2
fi

case "$1" in
"docker")
    if [ ! -f "$root/build/android-ndk-*-linux-x86_64.zip" ]; then
        echo "ERROR: Android NDK Linux 64-bit (x86) (android-ndk-*-linux-x86_64.zip) is not found on repository 'build' dir.">&2
        echo "">&2
        echo "You need Android NDK to build the toolchain. Please obtain from the following.">&2
        echo "https://developer.android.com/ndk/downloads">&2
        echo "">&2
        usage
        exit 1
    fi
    docker build -t $tag "$root"
    exit $?
    ;;
"toolchain")
    docker_build_if_not_exists
    shift
    docker run -v "$root:/zabuton" -w /zabuton $tag make $*
    exit $?
    ;;
"debug")
    docker run -v "$root:/zabuton" -w /zabuton -it $tag
    exit $?
    ;;
*)
    usage
esac

