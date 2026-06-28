#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
LOG_PATH="$ROOT_DIR/logs/01_environment.log"
DOC_PATH="$ROOT_DIR/docs/ENVIRONMENT_AUDIT.md"
QNN_HOST_ROOT="${QNN_HOST_ROOT:-/Users/preyashyadav/Documents/development/qualcomm/qairt/2.47.0.260601}"

mkdir -p "$ROOT_DIR/logs" "$ROOT_DIR/docs"

{
  echo "== baseline =="
  pwd
  ls -la
  find . -maxdepth 3 -type f | sort
  echo
  echo "== host =="
  date -u +"%Y-%m-%dT%H:%M:%SZ"
  uname -a
  uname -m
  sw_vers || true
  echo
  echo "== docker =="
  docker --version
  docker info
  docker compose version
  docker buildx ls
  echo
  echo "== disk =="
  df -h .
  echo
  echo "== memory =="
  vm_stat || true
  echo
  echo "== qnn host sdk =="
  test -f "$QNN_HOST_ROOT/QNN_README.txt"
  test -f "$QNN_HOST_ROOT/bin/envsetup.sh"
  sed -n '1,120p' "$QNN_HOST_ROOT/sdk.yaml"
  sed -n '1,120p' "$QNN_HOST_ROOT/QNN_README.txt"
  sed -n '1,220p' "$QNN_HOST_ROOT/QAIRT_ReleaseNotes.txt"
} 2>&1 | tee "$LOG_PATH"

cat > "$DOC_PATH" <<EOF
# Environment Audit

- Audit timestamp (UTC): $(date -u +"%Y-%m-%dT%H:%M:%SZ")
- Host architecture: $(uname -m)
- Docker context: $(docker info --format '{{.ClientInfo.Context}}')
- Docker server architecture: $(docker info --format '{{.Architecture}}')
- Docker server memory: $(docker info --format '{{.MemTotal}}')
- QNN SDK host path: $QNN_HOST_ROOT
- QNN SDK version: $(awk -F': ' '/^version:/ {print $2}' "$QNN_HOST_ROOT/sdk.yaml")
- QNN SDK build id: $(awk -F': ' '/^build_id:/ {print $2}' "$QNN_HOST_ROOT/sdk.yaml")
- QNN backend API version in sdk.yaml: $(awk -F': ' '/^qnn_backend_api_version:/ {print $2}' "$QNN_HOST_ROOT/sdk.yaml")
- Available disk: $(df -h . | awk 'NR==2 {print $4}')
- Docker Desktop status: $(docker info --format '{{.ServerVersion}} / {{.OperatingSystem}} / {{.Name}}')

Notes:
- The host is expected to be Apple Silicon, so linux/amd64 workloads will run through emulation.
- Full raw command output is preserved in \`logs/01_environment.log\`.
EOF
