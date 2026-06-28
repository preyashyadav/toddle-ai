#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_ROOT="${EXECUTORCH_WORKSPACE:-/workspace}"
ET_ROOT="$WORKSPACE_ROOT/executorch"
LOG_PATH="$ROOT_DIR/logs/02_executorch_install.log"

mkdir -p "$ROOT_DIR/logs" "$WORKSPACE_ROOT"

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "QNN_SDK_ROOT is not set."
  exit 1
fi

if [[ ! -f "$QNN_SDK_ROOT/QNN_README.txt" ]]; then
  echo "QNN_SDK_ROOT does not look correct: $QNN_SDK_ROOT"
  exit 1
fi

{
  echo "== setup_executorch =="
  date -u +"%Y-%m-%dT%H:%M:%SZ"
  echo "workspace root: $WORKSPACE_ROOT"
  echo "executorch root: $ET_ROOT"
  echo "qnn sdk root: $QNN_SDK_ROOT"

  export PYTHONPATH="${PYTHONPATH:-}"
  export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-}"
  # The Qualcomm SDK env script assumes PYTHONPATH is already defined.
  source "$QNN_SDK_ROOT/bin/envsetup.sh"

  if [[ ! -d "$ET_ROOT/.git" ]]; then
    git clone --recursive --branch v1.3.1 \
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
  ./backends/qualcomm/scripts/build.sh --help
  ./backends/qualcomm/scripts/build.sh --release
  python -m pip install --upgrade qai-hub-models
  python --version
  pip show torch executorch qai-hub-models || true
} 2>&1 | tee "$LOG_PATH"
