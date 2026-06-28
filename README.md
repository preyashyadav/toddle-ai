# ToddleAI — on-device toddler gait analysis (+ NPU Chat)

ToddleAI analyzes toddler walking videos **locally on-device** and produces gait observations, with
an on-device **Qwen** LLM assistant to explain results. Two complementary on-device engines:

| Workload | Engine | Where it runs |
|---|---|---|
| **Pose / gait** (33-landmark BlazePose skeleton incl. feet) | **MediaPipe Tasks PoseLandmarker** (`pose_landmarker_full.task`) | on-device, CPU (XNNPACK) or GPU |
| **LLM chat** (explain the recording) | **ExecuTorch + Qualcomm QNN** (Qwen `.pte`) | on-device, Hexagon NPU (SM8750) |

All inference is local; toggle airplane mode and both still work.

## Gait pipeline (quick start)

The pose model `app/src/main/assets/pose_landmarker_full.task` must be present (it is in this
checkout; if not, `app/src/main/assets/README.md` has the one-line download). Then:

```bash
# JDK 17 is required (AGP 8.5.2). Android Studio's bundled JBR also works.
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:installDebug      # or open in Android Studio and Run
```

Record (or import) a **side-view** clip of the toddler walking with **both feet visible**; ToddleAI
samples frames, runs PoseLandmarker on-device, detects heel-strike gait events, and reports temporal
observations. No detector/ROI staging or model conversion is needed — MediaPipe handles preprocessing
internally and emits the full lower-body landmark set the gait analysis requires.

> Why MediaPipe for pose: the bundled ExecuTorch `pose_landmark_*.pte` is the AI-Hub landmark **stage**
> that only outputs landmarks 0-24 (head → hips) with **no feet** (see `samples/pose/README.md`), so it
> cannot produce gait-quality lower-body landmarks. The `.task` model is the only on-device artifact
> that can. ExecuTorch/QNN remains the on-device backbone for the LLM chat below.

---

## NPU Chat — ExecuTorch + Qualcomm QNN on the Samsung S25 Ultra

The assistant runs an on-device **Qwen** LLM chat on the **Snapdragon 8 Elite Hexagon NPU** (SoC
**SM8750**), using **ExecuTorch** with the **Qualcomm QNN backend**. Tokens stream into a Compose chat
UI, fully offline.

> Scope: text chat only. (Image/camera demos with the InternVL3 VLM are intentionally deferred.)

---

## What you provide

This repo is the **app source**. Three artifacts are supplied by you (they are large/proprietary and
not committed):

| Artifact | Where it goes | Source |
|---|---|---|
| `executorch.aar` (QNN-enabled) | default: `build-artifacts/executorch.aar` or `executorchAarPath` / `EXECUTORCH_AAR_PATH` | built with `scripts/build_android_library.sh` (Linux/WSL) |
| Qualcomm `.so` (Hexagon v79) | auto-copied from `QNN_SDK_ROOT` at build time, or copied into `app/src/main/jniLibs/arm64-v8a/` | QNN SDK 2.37.0 |
| Model `.pte` + tokenizer | pushed to device `/data/local/tmp/llm/` | your downloaded `qwen…_SM8750` package |

See the `PLACE_*` notes in `app/libs/` and `app/src/main/jniLibs/arm64-v8a/` for exact file lists.

---

## Build & run

### 1. Build the QNN-enabled `executorch.aar` (one time, Linux/WSL)
```bash
source $QNN_SDK_ROOT/bin/envsetup.sh          # QNN SDK 2.37.0
export ANDROID_NDK_ROOT=/path/to/ndk/26c
export EXECUTORCH_ROOT=/path/to/executorch
export BUILD_AAR_DIR=$EXECUTORCH_ROOT/aar-out
cd "$EXECUTORCH_ROOT" && ./scripts/build_android_library.sh
```
Keep `aar-out/executorch.aar` in `build-artifacts/`, or point `executorchAarPath` / `EXECUTORCH_AAR_PATH`
at wherever you stored it.
If `QNN_SDK_ROOT` is set, Gradle stages the Qualcomm `.so` automatically during `preBuild`.

### 2. Open in Android Studio (Mac)
Set the SDK paths once, then open this folder and **Run** onto the phone:

```properties
# local.properties (recommended, already gitignored)
sdk.dir=/Users/you/Library/Android/sdk
qnnSdkRoot=/absolute/path/to/qairt/2.37.0
# Optional if you keep the AAR somewhere else:
# executorchAarPath=/absolute/path/to/executorch.aar
```

Environment variables work too:

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export QNN_SDK_ROOT=/absolute/path/to/qairt/2.37.0
# Optional override:
# export EXECUTORCH_AAR_PATH=/absolute/path/to/executorch.aar
```

The project defaults to `/Users/preyashyadav/Documents/personal-projects/toddle-ai/build-artifacts/executorch.aar`, which already exists in this checkout.

Open this folder, let it sync, then **Run** onto the S25 Ultra (USB debugging on).
*(The bundled JDK + Gradle handle the build; CLI `./gradlew installDebug` also works with JDK 17–21.)*

### 3. Push the model to the device
```bash
adb shell mkdir -p /data/local/tmp/llm
adb push hybrid_llama_qnn.pte /data/local/tmp/llm/
adb push tokenizer.json       /data/local/tmp/llm/
```
If the app reports the tokenizer is unsupported, convert once on the Mac and push that instead:
```bash
python -m pytorch_tokenizers.tools ... tokenizer.json -> tokenizer.bin   # see ExecuTorch docs
adb push tokenizer.bin /data/local/tmp/llm/
```

### 4. Chat
Launch the app → it loads the model → type a prompt → tokens stream from the NPU.

---

## Verify it's really on the NPU
```bash
adb logcat | grep -iE "qnn|htp|hexagon|executorch"
```
Look for the QNN/HTP backend initializing and `libQnnHtpV79Skel.so` loading, with **no CPU fallback**.
Toggle airplane mode — generation still works (100% on-device).

## What this checkout already has
- `build-artifacts/executorch.aar`
- `build-artifacts/pose_detector_cpu.pte`
- `build-artifacts/pose_detector_qnn.pte`
- `build-artifacts/pose_landmark_cpu.pte`
- `build-artifacts/pose_landmark_qnn.pte`

## What is still missing before the Android app can launch the QNN runtime
- `libQnnHtp.so`
- `libQnnHtpV79Stub.so`
- `libQnnSystem.so`
- `libQnnHtpV79Skel.so`

If `QNN_SDK_ROOT` points at your Qualcomm AI Engine Direct / QAIRT SDK, Gradle now stages these automatically during `preBuild`. If not, it will fail with a direct message telling you what path is missing.

---

## How it works (code map)
| File | Role |
|---|---|
| `ModelConfig.kt` | on-device file paths, model-presence checks |
| `ChatTemplate.kt` | renders the Qwen `<|im_start|>` prompt format (the runtime does NOT apply `chat_template.jinja`) |
| `LlmEngine.kt` | wraps `org.pytorch.executorch.extension.llm.LlmModule`; single engine thread; streaming `Flow<GenEvent>` |
| `ChatViewModel.kt` | model load state machine, conversation, tok/s |
| `ChatScreen.kt` | Compose UI: bubbles, input, setup help |

Because the `.pte` is QNN-delegated, `LlmModule.generate()` executes on the Hexagon NPU; the app code
is backend-agnostic.
