#!/bin/bash

root="$(realpath $(dirname "${BASH_SOURCE:-$0}"))"
tag=zabuton:latest

usage ()
{
    echo "AVR GCC toolchain for Android Build script"
    echo ""
    echo "Usage: $0 [docker|tolchain]"
    echo ""
    echo "  docker:"
    echo "    Build docker image for AVR GCC toolchain build."
    echo ""
    echo "  toolchain:"
    echo "    Build AVR GCC toolchain on docker."
    echo "" 
}

if [ "$#" -ne 1 ]; then
    usage
    exit 2
fi

case "$1" in
"docker")
    if [ ! -f "android-ndk-*.zip" ]; then
        echo "ERROR: Android NDK (android-ndk-*.zip) is not found on repository root.">&2
        echo "">&2
        echo "You need Android NDK for linux to build the toolchain. Please obtain from the following.">&2
        echo "https://developer.android.com/ndk/downloads">&2
        echo "">&2
        usage
        exit 1
    fi
    docker build -t $tag "$root"
    exit $?
    ;;
"toolchain")
    if [ "$(docker image ls zabuton:latest -q)" = "" ]; then
        docker build -t $tag "$root"
        exitcode=$?
        if [ $exitcode -ne 0 ]; then
            exit $exitcode
        fi
    fi
    docker run -v "$root:/zabuton" -w /zabuton $tag make
    exit $?
    ;;
*)
    usage
esac

