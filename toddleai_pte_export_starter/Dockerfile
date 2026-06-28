FROM --platform=linux/amd64 ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV VIRTUAL_ENV=/opt/venv
ENV ANDROID_SDK_ROOT=/opt/android/sdk
ENV ANDROID_HOME=/opt/android/sdk
ENV ANDROID_CMDLINE_TOOLS_ROOT=/opt/android/cmdline-tools/latest
ENV ANDROID_NDK_VERSION=27.2.12479018
ENV ANDROID_PLATFORM=34
ENV ANDROID_BUILD_TOOLS=34.0.0
ENV PATH=/opt/android/cmdline-tools/latest/bin:/opt/android/sdk/platform-tools:/opt/venv/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    wget \
    unzip \
    zip \
    rsync \
    jq \
    git \
    git-lfs \
    cmake \
    ninja-build \
    make \
    pkg-config \
    python3 \
    python3-dev \
    python3-pip \
    python3-venv \
    clang \
    lld \
    gcc \
    g++ \
    openjdk-17-jdk \
    libgl1 \
    libglib2.0-0 \
    && python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --upgrade pip setuptools wheel \
    && git lfs install --system \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/android/cmdline-tools /opt/android/sdk \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /tmp/commandlinetools.zip \
    && unzip -q /tmp/commandlinetools.zip -d /opt/android/cmdline-tools \
    && mv /opt/android/cmdline-tools/cmdline-tools /opt/android/cmdline-tools/latest \
    && yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null \
    && sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" \
      "platform-tools" \
      "platforms;android-${ANDROID_PLATFORM}" \
      "build-tools;${ANDROID_BUILD_TOOLS}" \
      "ndk;${ANDROID_NDK_VERSION}" \
    && rm -f /tmp/commandlinetools.zip

ENV ANDROID_NDK=${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}
ENV ANDROID_NDK_ROOT=${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}

WORKDIR /workspace/starter
CMD ["/bin/bash"]
