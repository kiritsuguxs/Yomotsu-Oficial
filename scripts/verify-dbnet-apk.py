#!/usr/bin/env python3
"""Inspect actual APK entries, keeping model weights external; report measured delta."""
import json
import os
from pathlib import Path
import sys
import zipfile

EXPECTED_Y19_DBNET_PADDLE_BASELINE_APK_BYTES = 116858368

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert not any("dbnet_detect.ncnn" in name for name in names), "DBNet model embedded in APK"
    native = archive.getinfo("lib/arm64-v8a/libyomotsu_dbnet.so")
    for license_name in ("GPL-3.0.txt", "ncnn-BSD-3-Clause.txt", "glslang-LICENSE.txt", "NDK-NOTICE.txt", "NOTICE.txt"):
        assert archive.getinfo(f"assets/dbnet-licenses/{license_name}").file_size > 0
with zipfile.ZipFile(sys.argv[2]) as baseline:
    entries = [item for item in baseline.infolist() if item.filename.endswith("-arm64.apk")]
    assert len(entries) == 1, "Ambiguous baseline APK"
    baseline_size = entries[0].file_size
    assert baseline_size == EXPECTED_Y19_DBNET_PADDLE_BASELINE_APK_BYTES, (
        "Archived Y19 DBNet+Paddle baseline APK size mismatch: "
        f"expected {EXPECTED_Y19_DBNET_PADDLE_BASELINE_APK_BYTES} bytes, actual {baseline_size} bytes"
    )
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
