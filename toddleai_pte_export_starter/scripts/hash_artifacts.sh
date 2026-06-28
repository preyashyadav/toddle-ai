#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
mkdir -p "$ROOT_DIR/reports"
find "$ROOT_DIR/artifacts" -type f \( -name '*.pte' -o -name '*.pt' \) -print0 | \
  xargs -0 shasum -a 256 > "$ROOT_DIR/reports/artifact_hashes.txt" || true
