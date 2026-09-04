#!/usr/bin/env bash
# Runs the pure DBNet unit suite against production sources, without Gradle/network.
# Prerequisites: JVM Kotlin/JUnit 5 runner with coroutines-core/test 1.11.0 and
# Kotlin 2.4.10, plus an Android SDK android.jar used only to resolve OcrImage's Uri.
# No Android framework calls or native inference are exercised by this suite.
#
# Usage (paths supplied by caller; no workstation-specific runtime is committed):
#   KOTLIN_TEST_RUNNER=/path/to/run-kotlin-tests-current \
#   ANDROID_JAR=/path/to/sdk/platforms/android-37.0/android.jar \
#   KOTLIN_TEST_JAVA_OPTS=-javaagent:/path/to/byte-buddy-agent.jar \
#     scripts/test-dbnet-core.sh
# ANDROID_HOME / ANDROID_SDK_ROOT may replace ANDROID_JAR for SDK platform 37.0.
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
runner=${KOTLIN_TEST_RUNNER:-run-kotlin-tests-current}
if ! command -v "$runner" >/dev/null 2>&1; then
    echo 'Set KOTLIN_TEST_RUNNER to the executable Kotlin/JUnit 5 runner.' >&2
    exit 2
fi
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
android_jar=${ANDROID_JAR:-${sdk_root:+$sdk_root/platforms/android-37.0/android.jar}}
if [[ -z "$android_jar" || ! -f "$android_jar" ]]; then
    echo 'Set ANDROID_JAR to an existing SDK android.jar (or set ANDROID_HOME).' >&2
    exit 2
fi
cd -- "$repo_root"
production=(
    app/src/main/java/eu/kanade/translation/detection/TextDetection.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetGeometry.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetLineGrouping.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetMlKitAssociation.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetCleanupMask.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetLease.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetPostprocessor.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetModelStore.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetNotification.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetPageCoordinator.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetPixels.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetReply.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetSession.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetStageMetrics.kt
    app/src/main/java/eu/kanade/translation/detection/DbnetWire.kt
    app/src/main/java/eu/kanade/translation/detection/ExperimentalDetectionRoute.kt
    app/src/main/java/eu/kanade/translation/detection/ExperimentalDbnetOcrEngine.kt
    app/src/main/java/eu/kanade/translation/recognizer/OcrEngine.kt
    app/src/main/java/eu/kanade/translation/recognizer/OcrEngineType.kt
    app/src/main/java/eu/kanade/translation/recognizer/OcrModels.kt
    app/src/main/java/eu/kanade/translation/recognizer/PaddleTextBlockMapper.kt
    app/src/main/java/eu/kanade/translation/recognizer/TextRecognizerLanguage.kt
    app/src/main/java/eu/kanade/translation/model/PageTranslation.kt
    app/src/main/java/eu/kanade/translation/model/TranslationCleanupGeometry.kt
    app/src/main/java/eu/kanade/translation/model/TranslationGeometry.kt
    core/common/src/main/kotlin/tachiyomi/core/common/preference/Preference.kt
    core/common/src/main/kotlin/tachiyomi/core/common/preference/PreferenceStore.kt
    core/common/src/main/kotlin/tachiyomi/core/common/preference/InMemoryPreferenceStore.kt
    domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt
)
tests=(app/src/test/java/eu/kanade/translation/detection/*Test.kt)
export EXTRA_CLASSPATH="$android_jar${EXTRA_CLASSPATH:+:$EXTRA_CLASSPATH}"
export ENABLE_KOTLIN_SERIALIZATION=1
exec "$runner" "${production[@]}" "${tests[@]}"
