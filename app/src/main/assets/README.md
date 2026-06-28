Place ToddleAI model files in this directory.

## Required for gait analysis (on-device pose)

- `pose_landmarker_full.task` — MediaPipe Tasks **PoseLandmarker** (BlazePose full, 33 landmarks
  **including the lower body**: knees, ankles, heels, foot-index). This is the model the gait
  pipeline actually runs on; it is selected automatically and runs on-device (CPU/XNNPACK or GPU).

  If missing, download it once (≈9 MB):

  ```bash
  curl -L -o app/src/main/assets/pose_landmarker_full.task \
    https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
  ```

## Optional (ExecuTorch benchmark / NPU experiments only — NOT gait-capable)

- `pose_landmark_cpu.pte`, `pose_landmark_qnn.pte` — Qualcomm AI-Hub MediaPipe-Pose **landmark
  stage**. These emit only landmarks 0-24 (head → hips, see `samples/pose/README.md`) and have **no
  foot landmarks**, so they cannot drive gait analysis. Kept for the Settings benchmark screen and
  the `samples/pose` NPU demo; they also require a separate detector + ROI crop stage (the detector
  `.pte` is not bundled in the app).
