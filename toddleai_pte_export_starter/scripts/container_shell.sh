#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$ROOT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
fi

mkdir -p \
  "$ROOT_DIR/workspace" \
  "$ROOT_DIR/logs" \
  "$ROOT_DIR/reports" \
  "$ROOT_DIR/artifacts/pte/xnnpack" \
  "$ROOT_DIR/artifacts/pte/qnn-fp16" \
  "$ROOT_DIR/artifacts/smoke-test" \
  "$ROOT_DIR/artifacts/exported-programs" \
  "$ROOT_DIR/tests/data" \
  "$ROOT_DIR/docs" \
  "$ROOT_DIR/android-handoff"

export DOCKER_DEFAULT_PLATFORM=linux/amd64
docker compose --env-file "$ENV_FILE" build toddleai-pte-export
docker compose --env-file "$ENV_FILE" run --rm toddleai-pte-export /bin/bash
