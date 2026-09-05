#!/usr/bin/env python3
"""Restrict Y22 to its approved models, Paddle buffers and conservative Bubble fit."""
import subprocess

BASE = "32306477fe4f650245c0a7c17aac4fc726e25ea4"
allowed = {
    "ppocr-sdk/src/main/assets/models/det/inference.onnx",
    "ppocr-sdk/src/main/assets/models/rec/inference.onnx",
    "ppocr-sdk/src/main/assets/models/rec/inference.yml",
    "ppocr-sdk/MODELS.md",
    ".github/workflows/build_tachiyomiat.yml",
    "scripts/verify-dbnet-scope.py",
    "app/src/androidTest/java/eu/kanade/translation/recognizer/PaddleOcrV6SmokeTest.kt",
    "ppocr-sdk/src/main/java/com/paddle/ocr/engine/ORTSessionManager.kt",
    "ppocr-sdk/src/main/java/com/paddle/ocr/preprocess/RecPreprocessor.kt",
    "app/src/main/java/eu/kanade/translation/presentation/SmartTranslationBlock.kt",
    "app/src/main/java/eu/kanade/translation/presentation/TranslationTextFit.kt",
    "app/src/test/java/eu/kanade/translation/recognizer/FloatTensorOutputTest.kt",
    "app/src/test/java/eu/kanade/translation/presentation/TranslationTextFitTest.kt",
    "app/src/androidTest/java/eu/kanade/translation/presentation/TranslationTextFitAndroidTest.kt",
}

subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
changed = subprocess.check_output(["git", "diff", "--name-only", BASE], text=True).splitlines()
assert set(changed) <= allowed, f"Unexpected changes: {set(changed) - allowed}"
print(f"Y22 scope verified against {BASE}: {len(changed)} files.")
print("Only the authorized Paddle buffers and Bubble fit production files may differ.")
print("Other production source, dependencies, identity, signing and updater are unchanged.")
