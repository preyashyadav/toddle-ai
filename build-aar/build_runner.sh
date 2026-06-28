#!/usr/bin/env bash
# Builds the native qnn_llama_runner for Android arm64 (to run the hybrid QNN model from adb shell).
set -euxo pipefail
ET=/work/executorch
cd "$ET"
# shellcheck disable=SC1091
source "$ET/.venv/bin/activate"
export QNN_SDK_ROOT=/qnn
export ANDROID_NDK_ROOT=/opt/ndk
export ANDROID_NDK=/opt/ndk
export PYTHONPATH="$ET/..:${PYTHONPATH:-}"

# Official QNN android build: core (install) + examples/qualcomm + oss_scripts/llama runner.
./backends/qualcomm/scripts/build.sh --skip_x86_64 --release

mkdir -p /out
find "$ET/build-android" -name 'qnn_llama_runner' -type f -exec cp -v {} /out/ \;
ls -l /out/
echo "RUNNER_BUILD_DONE"
