# Keep ExecuTorch JNI-facing classes and native callbacks.
-keep class org.pytorch.executorch.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
