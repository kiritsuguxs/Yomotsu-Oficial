#!/usr/bin/env python3
"""Reject edits outside the approved Y21 recovery and bounded mask experiment."""
from pathlib import Path
import subprocess

BASE = "origin/main"
allowed = {
    'app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt',
    '.github/workflows/build_tachiyomiat.yml',
    'app/build.gradle.kts',
    'scripts/verify-dbnet-scope.py',
    'app/src/main/java/eu/kanade/translation/ChapterTranslator.kt',
    'app/src/main/java/eu/kanade/translation/model/TranslationRecoveryPolicy.kt',
    'app/src/main/java/eu/kanade/translation/model/TranslationGeometry.kt',
    'app/src/main/java/eu/kanade/translation/detection/DbnetCleanupMask.kt',
    'app/src/test/java/eu/kanade/translation/model/TranslationRecoveryPolicyTest.kt',
    'app/src/test/java/eu/kanade/translation/model/TranslationGeometryTest.kt',
    'app/src/test/java/eu/kanade/translation/detection/DbnetCleanupMaskTest.kt',
}

subprocess.run(["git", "fetch", "--quiet", "origin", "main"], check=True)
changed = subprocess.check_output(["git", "diff", "--name-only", f"{BASE}...HEAD"], text=True).splitlines()
invalid = [path for path in changed if path not in allowed]
assert not invalid, f"Unexpected protected changes: {invalid}"

forbidden_imports = (
    "import com.paddle.",
    "import org.opencv.",
)
experimental_detection = Path("app/src/main/java/eu/kanade/translation/detection")
obsolete_dependencies = [
    f"{source}:{line_number}: {line}"
    for source in sorted(experimental_detection.rglob("*.kt"))
    for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1)
    if line.startswith(forbidden_imports)
]
assert not obsolete_dependencies, (
    "Experimental DBNet detection must not depend on Paddle crop recognition or OpenCV:\n"
    + "\n".join(obsolete_dependencies)
)

print(f"Scope verified against {BASE}: {len(changed)} files, only approved integration surface.")
print("Normal ML Kit/Paddle engines and weights, translators, memory/cache/glossary, "
      "SmartTranslationBlock/text fit, fonts, auto-scroll and existing migrations "
      "are byte-identical.")
print("Only the approved Y21 classification, geometry, mask, tests, version, and required "
      "workflow adaptations are allowlisted.")
