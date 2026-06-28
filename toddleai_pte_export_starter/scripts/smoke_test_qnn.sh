#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_ROOT="${EXECUTORCH_WORKSPACE:-/workspace}"
ET_ROOT="$WORKSPACE_ROOT/executorch"
LOG_PATH="$ROOT_DIR/logs/04_qnn_smoke_test.log"

mkdir -p "$ROOT_DIR/logs" "$ROOT_DIR/artifacts/smoke-test"

{
  cd "$ET_ROOT"
  source .venv/bin/activate
  export PYTHONPATH="${PYTHONPATH:-}"
  export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-}"
  source "$QNN_SDK_ROOT/bin/envsetup.sh"
  python -m examples.qualcomm.scripts.deeplab_v3 --help
  python -m examples.qualcomm.scripts.deeplab_v3 \
    -b build-android \
    -m SM8750 \
    --compile_only \
    --download
} 2>&1 | tee "$LOG_PATH"
