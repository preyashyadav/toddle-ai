# Put the Qualcomm QNN runtime `.so` files in this folder

Copy these from the **QNN SDK 2.37.0** for the Snapdragon 8 Elite / SM8750 (Hexagon **v79**):

From `$QNN_SDK_ROOT/lib/aarch64-android/`:
- `libQnnHtp.so`
- `libQnnHtpV79Stub.so`
- `libQnnSystem.so`
- (and any other `libQnn*.so` the runner references, e.g. `libQnnHtpPrepare.so`)

From `$QNN_SDK_ROOT/lib/hexagon-v79/unsigned/`:
- `libQnnHtpV79Skel.so`

> The ExecuTorch runtime `.so` (`libexecutorch.so`, `libexecutorch_jni.so`,
> `libqnn_executorch_backend.so`) are bundled INSIDE `executorch.aar` — do not copy those here.

Final layout:

```
app/src/main/jniLibs/arm64-v8a/
├── libQnnHtp.so
├── libQnnHtpV79Stub.so
├── libQnnHtpV79Skel.so
└── libQnnSystem.so
```
