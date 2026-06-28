#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_ROOT="${EXECUTORCH_WORKSPACE:-/workspace}"
EXAMPLES_ROOT="${EXAMPLES_ROOT:-$WORKSPACE_ROOT/executorch-examples}"
DEMO_APP="${DEMO_APP:-$EXAMPLES_ROOT/llm/android/LlamaDemo}"
AAR_SOURCE="${AAR_SOURCE:-$ROOT_DIR/android-handoff/executorch.aar}"
LOG_PATH="$ROOT_DIR/logs/05_llama_demo_setup.log"

mkdir -p "$ROOT_DIR/logs"

{
  echo "== setup_llama_demo =="
  date -u +"%Y-%m-%dT%H:%M:%SZ"
  echo "examples root: $EXAMPLES_ROOT"
  echo "demo app: $DEMO_APP"
  echo "aar source: $AAR_SOURCE"

  if [[ ! -f "$AAR_SOURCE" ]]; then
    echo "AAR not found at $AAR_SOURCE"
    exit 1
  fi

  if [[ ! -d "$EXAMPLES_ROOT/.git" ]]; then
    git clone --depth 1 https://github.com/meta-pytorch/executorch-examples.git "$EXAMPLES_ROOT"
  fi

  if [[ ! -d "$DEMO_APP" ]]; then
    echo "LlamaDemo app not found at $DEMO_APP"
    exit 1
  fi

  mkdir -p "$DEMO_APP/app/libs"
  cp "$AAR_SOURCE" "$DEMO_APP/app/libs/executorch.aar"

  rg -n "useLocalAar" "$DEMO_APP" -S
  ls -la "$DEMO_APP/app/libs"
} 2>&1 | tee "$LOG_PATH"
