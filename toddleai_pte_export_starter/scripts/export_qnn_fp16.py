#!/usr/bin/env python3
from export_mediapipe_pose import main

if __name__ == "__main__":
    raise SystemExit(
        main(
            [
                "--backend",
                "qnn",
                "--component",
                "both",
                "--soc",
                "SM8750",
                "--output-dir",
                "artifacts/pte/qnn-fp16",
            ]
        )
    )
