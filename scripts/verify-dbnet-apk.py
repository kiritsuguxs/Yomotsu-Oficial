#!/usr/bin/env python3
"""Inspect actual APK entries, keeping model weights external; report measured delta."""
import json
import os
from pathlib import Path
import sys
import zipfile

# Canonical size measured from the approved v0.20.4-Y19 DBNet+Paddle ARM64 APK.
EXPECTED_Y19_DBNET_PADDLE_BASELINE_APK_BYTES = 116858368

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert not any("dbnet_detect.ncnn" in name for name in names), "DBNet model embedded in APK"
    native = archive.getinfo("lib/arm64-v8a/libyomotsu_dbnet.so")
    for license_name in ("GPL-3.0.txt", "ncnn-BSD-3-Clause.txt", "glslang-LICENSE.txt", "NDK-NOTICE.txt", "NOTICE.txt"):
        assert archive.getinfo(f"assets/dbnet-licenses/{license_name}").file_size > 0
baseline_size = EXPECTED_Y19_DBNET_PADDLE_BASELINE_APK_BYTES
metrics = {
    "baseline_apk_bytes": baseline_size,
    "experimental_apk_bytes": apk.stat().st_size,
    "additional_apk_bytes": apk.stat().st_size - baseline_size,
    "dbnet_native_bytes": native.file_size,
    "dbnet_native_zip_bytes": native.compress_size,
    "external_model_bytes": 153023948,
    "weights_inside_apk": False,
}
report = json.dumps(metrics, indent=2) + "\n"
print(report)
Path("dist/dbnet-apk-metrics.json").write_text(report)
if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(summary, "a") as output:
        output.write("DBNet experimental packaging (bytes):\n```json\n" + report + "```\n")
