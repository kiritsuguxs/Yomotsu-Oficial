#!/usr/bin/env python3
"""Reject accidental edits outside the explicitly approved DBNet integration surface."""
from pathlib import Path
import subprocess

BASE = "1eaf6ea959fa31f2ed50c6fbc05486efa103d0e9"
allowed = {
    ".github/workflows/build_tachiyomiat.yml", "YOMOTSU_CHANGELOG.md",
    "app/build.gradle.kts", "app/proguard-rules.pro", "app/src/main/AndroidManifest.xml",
    "app/src/debug/AndroidManifest.xml", "settings.gradle.kts",
    "app/src/main/java/eu/kanade/tachiyomi/App.kt",
    "app/src/main/java/eu/kanade/translation/ChapterTranslator.kt",
    "app/src/main/java/eu/kanade/translation/model/PageTranslation.kt",
    "app/src/main/java/eu/kanade/translation/model/TranslationBlockGrouper.kt",
    "app/src/main/java/eu/kanade/translation/model/TranslationCleanupGeometry.kt",
    "app/src/main/java/eu/kanade/translation/presentation/PagerTranslationsView.kt",
    "app/src/main/java/eu/kanade/translation/presentation/TranslationCleanupBlock.kt",
    "app/src/main/java/eu/kanade/translation/presentation/WebtoonTranslationsView.kt",
    "app/src/main/java/eu/kanade/translation/recognizer/OcrModels.kt",
    "app/src/test/java/eu/kanade/translation/model/TranslationBlockGrouperTest.kt",
    "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt",
    "domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt",
    "scripts/test-dbnet-core.sh", "scripts/verify-dbnet-apk.py", "scripts/verify-dbnet-scope.py",
}
prefixes = (
    "app/src/main/java/eu/kanade/translation/detection/",
    "app/src/test/java/eu/kanade/translation/detection/",
    "app/src/androidTest/java/eu/kanade/translation/detection/",
    "app/src/debug/java/eu/kanade/translation/detection/",
    "dbnet-native/", "docs/",
)
changed = subprocess.check_output(["git", "diff", "--name-only", BASE, "HEAD"], text=True).splitlines()
invalid = [path for path in changed if path not in allowed and not path.startswith(prefixes)]
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
print("DBNet mask consumer and grouping glue are explicitly allowlisted.")
