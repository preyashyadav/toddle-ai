#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

phase() {
  echo
  echo "=============================="
  echo "$1"
  echo "=============================="
}

phase "Phase 1 - Environment verification"
"$SCRIPT_DIR/verify_environment.sh"

phase "Phase 3 - ExecuTorch installation"
"$SCRIPT_DIR/setup_executorch.sh"

phase "Phase 4 - Qualcomm smoke test"
"$SCRIPT_DIR/smoke_test_qnn.sh"

phase "Phase 5/6/7 - MediaPipe inspection"
python3 "$SCRIPT_DIR/inspect_mediapipe_pose.py" 2>&1 | tee "$ROOT_DIR/logs/05_mediapipe_inspection.log"

phase "Phase 8 - XNNPACK export"
python3 "$SCRIPT_DIR/export_xnnpack.py" 2>&1 | tee "$ROOT_DIR/logs/08_xnnpack_export.log"

phase "Phase 9 - QNN export"
python3 "$SCRIPT_DIR/export_qnn_fp16.py" 2>&1 | tee "$ROOT_DIR/logs/10_qnn_detector_export.log"

phase "Phase 10 - Validation reports"
python3 "$SCRIPT_DIR/validate_xnnpack.py"
python3 "$SCRIPT_DIR/validate_qnn.py"
"$SCRIPT_DIR/hash_artifacts.sh"

cat > "$ROOT_DIR/reports/final_status.json" <<'EOF'
{
  "environment_verified": true,
  "qnn_backend_built": false,
  "official_qnn_smoke_test": "not_run",
  "detector_eager_inference": "not_run",
  "landmark_eager_inference": "not_run",
  "detector_torch_export": "not_run",
  "landmark_torch_export": "not_run",
  "detector_xnnpack_pte": "not_run",
  "landmark_xnnpack_pte": "not_run",
  "detector_qnn_pte": "not_run",
  "landmark_qnn_pte": "not_run",
  "qnn_runtime_validation": "not_run_no_device",
  "full_npu_delegation_verified": false
}
EOF
