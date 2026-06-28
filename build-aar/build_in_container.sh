#!/usr/bin/env bash
# Runs INSIDE the builder container. Clones/updates executorch, sets up python, and builds the
# QNN-enabled Android AAR into /out/executorch.aar.
set -euxo pipefail

ET=/work/executorch
QNN_SDK_ROOT=${QNN_SDK_ROOT:-/qnn}
export QNN_SDK_ROOT
export ANDROID_NDK=${ANDROID_NDK:-/opt/ndk}
export ANDROID_SDK=${ANDROID_SDK:-/opt/android/sdk}
export ANDROID_ABIS=${ANDROID_ABIS:-arm64-v8a}     # only the S25 Ultra ABI -> faster build
export EXECUTORCH_BUILD_EXTENSION_LLM=ON
export PYTHON_EXECUTABLE=python3
export BUILD_AAR_DIR=/out
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"

# 1. Clone (persisted on the host bind mount; only clones on first run).
if [ ! -d "$ET/.git" ]; then
    git clone https://github.com/pytorch/executorch.git "$ET"
fi
cd "$ET"
git submodule sync
git submodule update --init --recursive

# 2. Python env + executorch host deps (needed for codegen/flatc during the cmake build).
if [ ! -d "$ET/.venv" ]; then
    python3 -m venv "$ET/.venv"
fi
# shellcheck disable=SC1091
source "$ET/.venv/bin/activate"
pip install --upgrade pip
./install_requirements.sh || pip install -r requirements-dev.txt || true

# 3. Build the AAR (QNN auto-enabled because QNN_SDK_ROOT is set).
bash scripts/build_android_library.sh

ls -lh /out/
echo "DONE: /out/executorch.aar"
