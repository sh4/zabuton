FROM ubuntu:latest

# Install prerequisute packages
RUN apt-get -o APT::Sandbox::User=root update -y && apt-get install -y \
    unzip \
    gcc \
    g++ \
    autoconf \
    automake \
    bison \
    flex \
    autopoint \
    texinfo \
    pkg-config \
    curl \
    make \
    rsync \
    wget \
    git \
    cmake \
    libtool

# Install automake latest version
RUN curl -O https://ftp.gnu.org/gnu/automake/automake-1.16.1.tar.xz && \
    tar Jxvf automake-1.16.1.tar.xz && \
    cd automake-1.16.1 && \
    ./configure && make -j 8 && make install && \
    cp -f /usr/share/aclocal/*.m4 /usr/local/share/aclocal

# Unzip android ndk
ADD ./android-ndk-*.zip /
RUN unzip /android-ndk-*.zip -d / && rm -f /android-ndk-*.zip
