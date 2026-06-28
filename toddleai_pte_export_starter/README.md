# ToddleAI — ExecuTorch/QNN PTE Export Starter

This starter is intentionally staged:

1. Build a Linux x86_64 Docker environment.
2. Mount the Qualcomm QNN SDK into the container.
3. Clone and build ExecuTorch with the Qualcomm backend.
4. Export an official sample first.
5. Inspect and export MediaPipe Pose detector and landmark components separately.
6. Build the Android ExecuTorch AAR with QNN support.
7. Copy the AAR into the LlamaDemo app.

## 0. Prerequisites

- Docker Desktop
- Qualcomm QNN SDK downloaded locally
- At least 30–40 GB free disk space
- On Apple Silicon, keep `--platform linux/amd64`; this uses emulation and can be slower.

The mounted QNN directory must contain `QNN_README.txt`.

The Android AAR flow in this starter installs Android SDK command-line tools and
NDK `27.2.12479018` inside the container. Upstream ExecuTorch Qualcomm docs were
validated with NDK `26c`, so if `27.2.12479018` fails in your environment,
switch the image to an NDK 26c package before debugging further.

## 1. Build the Docker image

```bash
cd toddleai_pte_export_starter
docker build --platform linux/amd64 -t toddleai-pte-export .
```

## 2. Start the container

Replace `/ABSOLUTE/PATH/TO/QNN_SDK` with your local SDK path.

```bash
docker run --rm -it \
  --platform linux/amd64 \
  -v "$PWD/workspace":/workspace \
  -v "/ABSOLUTE/PATH/TO/QNN_SDK":/opt/qnn:ro \
  -e QNN_SDK_ROOT=/opt/qnn \
  toddleai-pte-export
```

Inside the container:

```bash
bash /workspace/starter/scripts/setup_executorch.sh
```

The setup script clones ExecuTorch v1.3.1, installs it, builds the Qualcomm
backend, and installs Qualcomm AI Hub Models.

## 2a. Build the Android AAR with QNN support

Inside the container:

```bash
bash /workspace/starter/scripts/build_android_aar_qnn.sh
```

This script:

- clones ExecuTorch `v1.3.1` into `/workspace/executorch` if needed
- initializes submodules
- installs Python requirements
- exports `EXECUTORCH_ROOT`, `QNN_SDK_ROOT`, `ANDROID_NDK`, `ANDROID_SDK`, and `BUILD_AAR_DIR`
- runs `scripts/build_android_library.sh`
- copies the output AAR to `/workspace/starter/android-handoff/executorch.aar`

The upstream `scripts/build_android_library.sh` enables QNN automatically when
`QNN_SDK_ROOT` is set.

## 2b. Copy the AAR into LlamaDemo

Inside the container:

```bash
bash /workspace/starter/scripts/setup_llama_demo.sh
```

This clones `executorch-examples` into `/workspace/executorch-examples` if
needed, copies the built AAR into `llm/android/LlamaDemo/app/libs/executorch.aar`,
and verifies that the demo app contains the `useLocalAar` flag wiring.

## 3. Smoke-test the Qualcomm toolchain

Inside the container:

```bash
cd /workspace/executorch
source .venv/bin/activate
source "$QNN_SDK_ROOT/bin/envsetup.sh"
export PYTHONPATH=/workspace:$PYTHONPATH

python -m examples.qualcomm.scripts.deeplab_v3 \
  -b build-android \
  -m SM8750 \
  --compile_only \
  --download
```

Do not proceed to MediaPipe until this produces a `.pte`.

## 4. Inspect MediaPipe Pose before exporting

```bash
cd /workspace/starter
source /workspace/executorch/.venv/bin/activate
source "$QNN_SDK_ROOT/bin/envsetup.sh"
export PYTHONPATH=/workspace:$PYTHONPATH

python scripts/export_mediapipe_pose.py \
  --backend inspect \
  --component both
```

This prints:

- component class
- input specification
- example tensor shapes
- output tensor shapes
- whether `torch.export` succeeds

## 5. Generate CPU/XNNPACK PTEs first

```bash
python scripts/export_mediapipe_pose.py \
  --backend xnnpack \
  --component both \
  --output-dir /workspace/output
```

Expected outputs:

```text
/workspace/output/mediapipe_pose_detector_xnnpack.pte
/workspace/output/mediapipe_pose_landmark_xnnpack.pte
```

If XNNPACK export fails, fix graph-export issues before trying QNN.

## 6. Generate SM8750 QNN/HTP PTEs

```bash
python scripts/export_mediapipe_pose.py \
  --backend qnn \
  --component both \
  --soc SM8750 \
  --output-dir /workspace/output
```

Expected outputs:

```text
/workspace/output/mediapipe_pose_detector_sm8750_qnn_fp16.pte
/workspace/output/mediapipe_pose_landmark_sm8750_qnn_fp16.pte
```

## 7. Important limitations

- This script exports the neural components, not the entire MediaPipe Python app.
- Camera conversion, cropping, detector decoding, ROI transforms, landmark
  coordinate remapping, smoothing, and gait analysis belong in Android/Kotlin.
- A successfully created `.pte` does not prove that every node was delegated.
  Validate delegation and numerical correctness on the S25 Ultra.
- Keep the QNN SDK version used for export aligned with the QNN runtime packaged
  in the Android application.
- If the detector graph fails because of postprocessing, start with the landmark
  model, then move detector postprocessing outside the graph or use a simpler
  detector.
