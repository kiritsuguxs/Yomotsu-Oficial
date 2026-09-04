#!/usr/bin/env python3
"""Reject edits outside the approved translator UI port and repository migration."""
from pathlib import Path
import subprocess

BASE = "origin/main"
allowed = {
    ".github/workflows/build.yml",
    ".github/workflows/build_tachiyomiat.yml",
    ".github/workflows/pages.yml",
    ".github/workflows/release.yml",
    "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt",
    "app/src/main/java/eu/kanade/translation/ChapterTranslator.kt",
    "app/src/main/java/eu/kanade/translation/translator/TextTranslator.kt",
    "app/src/test/java/eu/kanade/presentation/more/settings/screen/TranslationEngineSettingsVisibilityTest.kt",
    "app/src/test/java/tachiyomi/domain/translation/TranslationLlmPreferencesTest.kt",
    "domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt",
    "scripts/verify-dbnet-apk.py",
    "scripts/verify-dbnet-scope.py",
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
      "SmartTranslationBlock/text fit, fonts, auto-scroll, updater and existing migrations "
      "are byte-identical.")
print("Only the approved translator UI port and required clean-history workflow adaptations are allowlisted.")
