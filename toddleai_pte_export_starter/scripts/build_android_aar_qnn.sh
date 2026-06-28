#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_ROOT="${EXECUTORCH_WORKSPACE:-/workspace}"
ET_ROOT="${EXECUTORCH_ROOT:-$WORKSPACE_ROOT/executorch}"
ET_REF="${EXECUTORCH_REF:-v1.3.1}"
LOG_PATH="$ROOT_DIR/logs/03_android_aar_qnn.log"

mkdir -p "$ROOT_DIR/logs" "$ROOT_DIR/android-handoff" "$WORKSPACE_ROOT"

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "QNN_SDK_ROOT is not set."
  exit 1
fi

if [[ ! -d "$QNN_SDK_ROOT/lib/aarch64-android" ]]; then
  echo "Expected Android QNN libraries under $QNN_SDK_ROOT/lib/aarch64-android"
  exit 1
fi

if [[ ! -d "$QNN_SDK_ROOT/lib/x86_64-linux-clang" ]]; then
  echo "Expected Linux host QNN libraries under $QNN_SDK_ROOT/lib/x86_64-linux-clang"
  exit 1
fi

if [[ -z "${ANDROID_NDK:-}" ]]; then
  echo "ANDROID_NDK is not set."
  exit 1
fi

if [[ ! -f "$ANDROID_NDK/NOTICE" ]]; then
  echo "ANDROID_NDK does not look correct: $ANDROID_NDK"
  exit 1
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_SDK_ROOT is not set."
  exit 1
fi

{
  echo "== build_android_aar_qnn =="
  date -u +"%Y-%m-%dT%H:%M:%SZ"
  echo "workspace root: $WORKSPACE_ROOT"
  echo "executorch root: $ET_ROOT"
  echo "executorch ref: $ET_REF"
  echo "qnn sdk root: $QNN_SDK_ROOT"
  echo "android sdk root: $ANDROID_SDK_ROOT"
  echo "android ndk: $ANDROID_NDK"

  export PYTHONPATH="${PYTHONPATH:-}"
  export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-}"
  export EXECUTORCH_ROOT="$ET_ROOT"
  export ANDROID_SDK="${ANDROID_SDK_ROOT}"
  export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  export ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_NDK}"
  export BUILD_AAR_DIR="$ROOT_DIR/android-handoff"
  export EXECUTORCH_CMAKE_BUILD_TYPE="${EXECUTORCH_CMAKE_BUILD_TYPE:-Release}"
  export QNN_SDK_ROOT
  # The Qualcomm SDK env script assumes PYTHONPATH is already defined.
  source "$QNN_SDK_ROOT/bin/envsetup.sh"

  if [[ ! -d "$ET_ROOT/.git" ]]; then
    git clone --branch "$ET_REF" --depth 1 \
      https://github.com/pytorch/executorch.git "$ET_ROOT"
  fi

  cd "$ET_ROOT"
  git submodule sync --recursive
  git submodule update --init --recursive
  git rev-parse HEAD
  git describe --tags --always

  if [[ ! -d .venv ]]; then
    python3 -m venv .venv
  fi

  source .venv/bin/activate
  python -m pip install --upgrade pip setuptools wheel
  ./install_executorch.sh
  python -m pip install -r backends/qualcomm/requirements.txt

  rm -rf cmake-out-android-arm64-v8a cmake-out-android-x86_64 cmake-out-android-so
  bash scripts/build_android_library.sh

  ls -la "$BUILD_AAR_DIR"
  if [[ ! -f "$BUILD_AAR_DIR/executorch.aar" ]]; then
    echo "Expected AAR not found at $BUILD_AAR_DIR/executorch.aar"
    exit 1
  fi
} 2>&1 | tee "$LOG_PATH"
