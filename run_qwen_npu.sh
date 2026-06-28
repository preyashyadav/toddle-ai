#!/usr/bin/env bash
# Run the Qwen3-1.7B hybrid QNN model on the S25 Ultra's Hexagon NPU via the shell domain.
# (The installed app is blocked from the cDSP by Samsung SELinux; the adb shell domain is not.)
#
# Usage: ./run_qwen_npu.sh "your prompt here" [seq_len]
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
D=/data/local/tmp/qwenrun
PROMPT="${1:-I would like to learn python, could you teach me with a simple example?}"
SEQ_LEN="${2:-512}"

# One-time push is assumed already done (runner + libs + model in $D). Re-push runner/libs only:
#   $ADB push <runner-out>/* $D/   ;  $ADB push <model>/hybrid_llama_qnn.pte $D/  ; etc.

"$ADB" shell "cd $D && LD_LIBRARY_PATH=$D ADSP_LIBRARY_PATH=$D ./qnn_llama_runner \
  --decoder_model_version qwen3 \
  --model_path hybrid_llama_qnn.pte \
  --tokenizer_path tokenizer.json \
  --eval_mode 1 --temperature 0 --seq_len $SEQ_LEN \
  --prompt '$PROMPT'" 2>&1 | grep -viE "Reading file|midr_el1|threadpool"

echo
echo "=== full response ==="
"$ADB" shell "cat $D/outputs.txt"
