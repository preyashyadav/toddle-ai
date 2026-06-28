#!/usr/bin/env python3
import json
from pathlib import Path


def main() -> int:
    report_path = Path("reports/qnn_numerics.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    if not report_path.exists():
      report_path.write_text(
          json.dumps({"status": "not_run_no_device"}, indent=2) + "\n"
      )
    print(report_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
