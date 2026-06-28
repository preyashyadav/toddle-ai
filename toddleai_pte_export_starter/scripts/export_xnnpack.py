#!/usr/bin/env python3
from export_mediapipe_pose import main

if __name__ == "__main__":
    raise SystemExit(
        main(
            [
                "--backend",
                "xnnpack",
                "--component",
                "both",
                "--output-dir",
                "artifacts/pte/xnnpack",
            ]
        )
    )
