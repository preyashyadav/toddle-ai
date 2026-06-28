# Ubuntu builder for the QNN-enabled ExecuTorch Android AAR.
# Heavy apt + Android SDK/NDK layers are cached here so the executorch build can re-run quickly.
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        git curl wget unzip zip ca-certificates \
        openjdk-17-jdk \
        cmake ninja-build build-essential \
        python3 python3-pip python3-venv python-is-python3 \
        libssl-dev ccache \
    && rm -rf /var/lib/apt/lists/*

# Android command-line tools + SDK packages (platform 34, build-tools 34, NDK r26d).
ENV ANDROID_SDK=/opt/android/sdk
ENV ANDROID_HOME=$ANDROID_SDK
RUN mkdir -p "$ANDROID_SDK/cmdline-tools" && cd "$ANDROID_SDK/cmdline-tools" \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmt.zip \
    && unzip -q cmt.zip && rm cmt.zip && mv cmdline-tools latest
ENV PATH=$ANDROID_SDK/cmdline-tools/latest/bin:$ANDROID_SDK/platform-tools:$PATH
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.3.11579264" >/dev/null

ENV ANDROID_NDK=$ANDROID_SDK/ndk/26.3.11579264
RUN ln -s "$ANDROID_NDK" /opt/ndk
