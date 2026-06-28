# Put `executorch.aar` in this folder

This app needs the **QNN-enabled** ExecuTorch Android library named exactly `executorch.aar`.

Build it once on a Linux / WSL host (per the official
[Qualcomm backend doc](https://docs.pytorch.org/executorch/stable/backends-qualcomm.html)):

```bash
source $QNN_SDK_ROOT/bin/envsetup.sh          # QNN SDK 2.37.0
export ANDROID_NDK_ROOT=/path/to/ndk/26c
export EXECUTORCH_ROOT=/path/to/executorch
export BUILD_AAR_DIR=$EXECUTORCH_ROOT/aar-out
cd "$EXECUTORCH_ROOT" && ./scripts/build_android_library.sh
```

Then copy `aar-out/executorch.aar` into this directory:

```
app/libs/executorch.aar
```

The stock Maven `org.pytorch:executorch-android` is CPU-only and CANNOT load a QNN `.pte`, which is
why a locally built QNN AAR is required.
