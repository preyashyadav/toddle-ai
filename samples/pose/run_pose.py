#!/usr/bin/env python3
"""
Run MediaPipe-Pose (BlazePose) on the Samsung S25 Ultra Hexagon NPU, end to end.

The two QNN .pte models execute on-device via `qnn_executor_runner` in the adb-shell
domain (the installed app is blocked from the cDSP by Samsung SELinux; the shell is not).
This host script does image pre/post-processing and shells out to adb for the NPU runs.

  host: image --letterbox--> raw tensor
  adb : push raw -> run pose_detector_qnn.pte on NPU -> pull 4 output tensors
  host: decode + NMS -> rotated ROI -> 256 crop --> raw tensor
  adb : push raw -> run pose_landmark_qnn.pte on NPU -> pull scores + landmarks
  host: map landmarks back to image -> draw skeleton -> save annotated PNG

Usage:
  ./.venv/bin/python run_pose.py --image person.jpg --out pose_out.png
Prerequisites (one-time, done by setup in README):
  - adb on PATH (or $ADB), device connected, USB debugging on
  - QNN libs + qnn_executor_runner staged on device (the script stages them if missing)
"""
import argparse
import os
import subprocess
import sys
import numpy as np
from PIL import Image

import mediapipe_pose as mp

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
DEVICE_DIR = "/data/local/tmp/poserun"
# Reuse the QNN runtime libs already staged for the Qwen demo.
LIB_SRC_DIR = "/data/local/tmp/qwenrun"
RUNNER_LOCAL = os.path.expanduser(
    "~/work/etbuild/executorch/build-android/examples/qualcomm/executor_runner/qnn_executor_runner"
)
HERE = os.path.dirname(os.path.abspath(__file__))


def adb(*args, **kw):
    return subprocess.run([ADB, *args], capture_output=True, text=True, **kw)


def adb_sh(cmd):
    r = adb("shell", cmd)
    return r.stdout + r.stderr


def ensure_device_staged(detector_pte, landmark_pte):
    """Stage runner + QNN libs + both models on device (idempotent)."""
    adb_sh(f"mkdir -p {DEVICE_DIR}/out")
    have = adb_sh(f"ls {DEVICE_DIR}")
    if "qnn_executor_runner" not in have:
        if not os.path.exists(RUNNER_LOCAL):
            sys.exit(f"qnn_executor_runner not found at {RUNNER_LOCAL}\n"
                     f"Build it via backends/qualcomm/scripts/build.sh (see README).")
        print("staging qnn_executor_runner + QNN libs on device ...")
        adb("push", RUNNER_LOCAL, f"{DEVICE_DIR}/qnn_executor_runner")
        adb_sh(f"cp {LIB_SRC_DIR}/libQnn*.so {LIB_SRC_DIR}/libc++_shared.so "
               f"{LIB_SRC_DIR}/libqnn_executorch_backend.so {DEVICE_DIR}/")
    for name, path in [("pose_detector_qnn.pte", detector_pte),
                       ("pose_landmark_qnn.pte", landmark_pte)]:
        if name not in have:
            print(f"pushing {name} ...")
            adb("push", path, f"{DEVICE_DIR}/{name}")


def run_on_npu(model, input_bytes, out_sizes):
    """Push input, run model on NPU, return list of np.float32 arrays (flat) per out_sizes."""
    in_local = os.path.join(HERE, "_in.raw")
    with open(in_local, "wb") as f:
        f.write(input_bytes)
    adb("push", in_local, f"{DEVICE_DIR}/_in.raw")
    adb_sh(f"rm -f {DEVICE_DIR}/out/output_*.raw; echo _in.raw > {DEVICE_DIR}/in_list.txt")
    log = adb_sh(
        f"cd {DEVICE_DIR} && LD_LIBRARY_PATH={DEVICE_DIR} ADSP_LIBRARY_PATH={DEVICE_DIR} "
        f"./qnn_executor_runner --model_path {model} --input_list_path in_list.txt "
        f"--output_folder_path {DEVICE_DIR}/out 2>&1")
    if "inference took" not in log and "is loaded" not in log:
        sys.exit(f"NPU run failed for {model}:\n{log[-800:]}")
    outs = []
    for i, nbytes in enumerate(out_sizes):
        local = os.path.join(HERE, f"_out_{i}.raw")
        adb("pull", f"{DEVICE_DIR}/out/output_0_{i}.raw", local)
        arr = np.fromfile(local, dtype=np.float32)
        if arr.size * 4 != nbytes:
            sys.exit(f"output {i} size {arr.size*4}B != expected {nbytes}B (layout/model mismatch)")
        outs.append(arr)
    return outs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--out", default="pose_out.png")
    ap.add_argument("--detector", default=os.path.expanduser("~/work/s25_models/pose_detector_qnn.pte"))
    ap.add_argument("--landmark", default=os.path.expanduser("~/work/s25_models/pose_landmark_qnn.pte"))
    ap.add_argument("--layout", choices=["nhwc", "nchw"], default="nchw",
                    help="input tensor layout the .pte expects (verified NCHW on these models)")
    args = ap.parse_args()

    img = Image.open(args.image)
    print(f"image {img.size} layout={args.layout}")
    ensure_device_staged(args.detector, args.landmark)
    anchors = mp.generate_anchors()

    # --- Stage 1: detector on NPU ---
    sq128, scale, px, py = mp.letterbox(img, mp.DET_SIZE)
    det_outs = run_on_npu("pose_detector_qnn.pte",
                          mp.to_input_raw(sq128, args.layout),
                          out_sizes=[512 * 12 * 4, 384 * 12 * 4, 512 * 4, 384 * 4])
    coords = np.concatenate([det_outs[0].reshape(512, 12), det_outs[1].reshape(384, 12)], 0)
    scores = np.concatenate([det_outs[2], det_outs[3]], 0)
    print(f"detector: max score {mp._sigmoid(scores).max():.3f} "
          f"(threshold {mp.MIN_DET_SCORE})")
    dets = mp.nms(mp.decode_detections(coords, scores, anchors))
    if not dets:
        sys.exit("No pose detected (max score below threshold). "
                 "If the person is clearly visible, try --layout nchw.")
    det = dets[0]
    print(f"detector: {len(dets)} pose(s), best score {det['score']:.3f}")

    # --- Stage 2: landmark on NPU ---
    _, _, _, corners = mp.roi_from_detection(det, scale, px, py)
    coeffs = mp.affine_256_to_orig(corners)
    crop = mp.crop_roi_256(img, coeffs)
    lm_outs = run_on_npu("pose_landmark_qnn.pte",
                         mp.to_input_raw(np.asarray(crop, np.uint8), args.layout),
                         out_sizes=[4, 25 * 4 * 4])
    lm_flag = float(lm_outs[0][0])
    landmarks = lm_outs[1].reshape(mp.NUM_LANDMARKS, 4)
    print(f"landmark: presence flag {lm_flag:.3f} (threshold {mp.MIN_LM_SCORE})")
    pts = mp.landmarks_to_orig(landmarks, coeffs)

    out = mp.draw_pose(img, pts, roi_corners=corners)
    out.save(args.out)
    print(f"saved {args.out}"
          + ("" if lm_flag >= mp.MIN_LM_SCORE else "  [WARN: low presence flag — pose may be off]"))


if __name__ == "__main__":
    main()
