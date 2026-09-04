# Y19 DBNet experimental execution record

Branch: `agent/y19-yakuyomi-dbnet-test1`. Official baseline:
`1eaf6ea959fa31f2ed50c6fbc05486efa103d0e9` (`yomotsu-independent`).
The user's approval resumes the architecture documented in the spec amendment;
it is not approval for official promotion, PR, merge or release.

## Behavior and removal boundary

The independent preference `dbnet_experimental` defaults to false. The existing
OCR engine remains the fallback and is invoked without creating DBNet models,
native handles or Paddle region recognition when this switch is off. Enable in
translation/OCR settings; use English source text on ARM64. First use streams
and verifies the two pinned model files in `files/models/dbnet-v3` (153,023,948
bytes total); no model weights are packaged. Both upstream files were also downloaded
to scratch storage and their SHA-256 digests independently matched the pins. Other languages/ABIs retain the
selected existing OCR with a warning. Technical failures disable the experiment for the remainder of that OCR session.
Valid empty detections/recognition fall back for that page only, preserving DBNet
for later pages. UI notifications are dispatched to Main and cannot interrupt OCR. Translation caches are unchanged; test on
untranslated pages or explicitly retranslate through the existing UI.

Only `:dbnet` loads NCNN. The non-exported service receives a read-only temporary
image descriptor and returns bounded neutral geometry. Binder death, timeout,
invalid response and model/NCNN errors take the existing OCR route. Worker
ownership prevents an unrelated/rejected client from stopping another request.
JNI validates shapes, packing, buffers and opaque handles, returning actual DB
and mask dimensions. Input keeps aspect ratio, then pads right/bottom to multiples
of 256 and normalizes RGB to [-1,1]. No upstream inpainting mask is propagated.

The additive Paddle adapter reuses the existing public crop, CTC recognition,
aspect-ratio batches and text mapper, restores detector order, and returns an
existing OcrPage. It never invokes Paddle detection. The public Paddle session
loader still loads both small existing Paddle assets; those files and all SDK
source are unchanged. Recognition images are sampled to at most 2048 per side
because existing bitmap conversion has temporary copies. This can reduce tiny
text accuracy on very tall pages and needs phone evaluation.

## Test-first evidence

Each behavioral unit was introduced through a failing focused test run, then a
minimal implementation and passing rerun. RED logs are local execution evidence;
the test sources remain versioned and reproducible through Gradle.

| Unit | Observed RED | Observed GREEN |
|---|---|---|
| Geometry/postprocessing | normalization, scale, clipping, component expectations failed before implementation | 30 tests |
| Model store | 6 failed / 1 baseline passed | 7 tests |
| Optional route | 4 failed / 2 baseline passed | 6 tests initially |
| NCNN session | 4 failed | 4 tests |
| IPC wire/reply | 4 failed / 2 baseline passed | 6 tests |
| Pixel normalization/sampling | normalization failed, then new sampling failed | 2 tests |
| Region recognition ordering/batching | 2 failed | 2 tests |
| Worker ownership | 2 failed | 2 tests |
| Cleanup/OOM fallback and blank-page continuation | cleanup/OOM escaped; a blank page disabled later detection | 8 route tests |
| UI notifications | inline feedback assertion and UI exception failed | 2 tests |
| Native buffer guards | invalid contract/rank assertions aborted as expected | ASan/UBSan guards pass |

The preferences test additionally verifies default false and the unchanged OCR
preference key/value. The combined local production-source suite passed **64/64**
with Kotlin 2.4.10, coroutines 1.11.0 and JUnit 5. Existing pure Paddle baseline
configuration/batch tests passed **4/4**. No Android stubs replace implementation.
`android.jar` is used only for unresolved Android type declarations in pure tests.

An additional real-file probe streamed the actual pinned 153,023,948-byte pair
through DbnetModelStore into a temporary directory and verified it again with
`java -Xmx32m` (reported max heap 33,554,432 bytes), without any network retry.
This validates bounded model-file handling, not NCNN inference memory.

The unequal-axis geometry fixture was strengthened and a scratch mutant using X
scale for Y failed that test; correct production scaling passed. Native sanitizer
checks run on host; LeakSanitizer alone is disabled locally because of sandbox
/proc limitations. ARM64 JNI CMake build with NDK 28.2.13676358 and CMake 3.22.1
succeeded; ELF load segments have 16 KiB alignment. This is not device inference.

## Full Android verification

Local full Gradle was attempted after preparing Java 21, SDK and Gradle 9.6.1,
but repository access to dl.google.com was blocked by environment policy. Offline
Gradle lacks the Kotlin serialization plugin; these are environment failures,
not TDD RED results. No repository dependency configuration was weakened.

The temporary-branch build workflow now executes all `testDebugUnitTest` tasks,
SQLDelight migration verification, native sanitizer checks and the signed full
release APK build. A separate Android emulator job tests death of a disposable
worker and caller fallback; its assertion requires a Binder-death result so bind
failures/timeouts cannot produce a false pass. This x86_64 emulator test exercises
process containment, not ARM64 NCNN inference. APK checks retain Y17 identity,
signature and Paddle hashes, reject embedded DBNet weights, check notices and
compare actual APK bytes to the original Y17 artifact from run33345437700.

Initial CI run 33348901249 passed all Android unit tests, SQLDelight verification
and the Android emulator worker-death instrumentation (one test).
Its next native-host test step failed because the workflow omitted the include
path; adding `-I dbnet-native/src/main/cpp` reproduced the passing local sanitizer
command. Final verification succeeded in [GitHub Actions run 33349457520](https://github.com/kiritsuguxs/Yomotsu/actions/runs/33349457520),
on code commit `ffd53db5677b4a1c1eacc0e784cfc08f23d73bbf`:

- All Android/JVM unit suites: **321 tests, 0 failures/errors/skips**, including
  the 64 DBNet tests; SQLDelight migration verification passed.
- C++ buffer contracts: **42 assertions**, ASan/UBSan passed in CI.
- Android 15 x86_64 emulator: **1 worker-death test passed**, caller survived and
  received the specific Binder-death failure used by the existing-OCR fallback.
- `assembleRelease -Penable-updater`: successful full build, including ARM64.
- APK identity remains `app.mihon.tachiyomiat`, versionCode77 / versionName0.20.4-Y17.
- Canonical signing certificate SHA-256 unchanged:
  `0a72eafb442d14f10893a7cbd4b6034292d18d786dceda0f55f8d854a515ede6`.
- Existing Paddle model hashes, DBNet license assets and absence of DBNet weights
  inside the APK were checked against the actual signed APK.
- New detector Kotlin sources pass ktlint1.8.0 using repository rules; existing
  protected source files were not mass-formatted.

The experimental APK and metrics are in artifact
**Yomotsu-Y19-DBNet-experimental-arm64**, ID9743265871:
[download artifact](https://github.com/kiritsuguxs/Yomotsu/actions/runs/33349457520/artifacts/9743265871).
The ZIP also contains `dbnet-apk-metrics.json`. GitHub may require sign-in to download.
The artifact ZIP SHA-256 is
`835e2b7ec24df73ee65909878677ff530680bea01cd479e5a9f63d53398a10a0`.
This is the artifact ZIP digest, not an asserted APK digest.

| Measured item | Bytes | Decimal MB |
|---|---:|---:|
| Baseline Y17 ARM64 APK (run33345437700) | 107,609,006 | 107.609 |
| Experimental ARM64 APK | 116,858,368 | 116.858 |
| **Additional APK size** | **9,249,362** | **9.249** |
| Packaged ARM64 DBNet native library | 9,163,808 | 9.164 |
| External DBNet param | 13,392 | 0.013 |
| External DBNet bin | 153,010,556 | 153.011 |
| **Separate model pair** | **153,023,948** | **153.024** |

The APK delta is a real binary-to-binary comparison, not an estimate from the
source archive. Model files were independently downloaded and SHA-256 verified.
The final documentation-only commit after the tested code does not change any
source/build/workflow input; it records these results and preserves the tested
APK commit explicitly. No PR, merge, release or official-branch update occurred. Phone model inference, quality, memory pressure and throughput have
not been verified. Process separation contains native fatal signals but cannot
guarantee the OS will never kill the reader under system-wide low memory.

## Attribution

See `docs/yakuyomi-dbnet-upstream.md` for frozen source/model provenance and
`dbnet-native/src/main/assets/dbnet-licenses/NOTICE.txt` for bundled GPL, NCNN,
glslang and NDK runtime notices. Existing licenses/credits remain. The upstream
native archives lack precise vendor source commits/build recipe; that provenance
limitation is disclosed rather than invented. APK artifacts are experimental,
with corresponding source on this branch; there is no official release.

## Residual phone-test limits

- Multi-line DBNet components can be imperfect input for Paddle's line CTC model;
  partial recognition does not automatically mean runtime failure or trigger
  whole-page fallback. This deliberately does not import Houri OCR or add a new
  splitter/grouping algorithm. Inspect complete speech bubbles when comparing.
- Closing a worker is asynchronous. An immediate engine change can briefly reach
  the prior worker owner; it safely falls back rather than forcing concurrent use.
- Metadata-only Logcat tag `YomotsuDBNet` reports actual tensor shapes, detected/
  recognized counts and technical fallback reason. No page text is logged.

## Complete changed-file list against the official baseline

The following 63 paths comprise the experiment and its documentation.
All paths outside this approved surface are unchanged; the few existing app
files contain only the preference, wrapper, worker guard, manifest and build hooks
reviewed above. `scripts/verify-dbnet-scope.py` enforces this boundary in CI.

- `.github/workflows/build_tachiyomiat.yml`
- `YOMOTSU_CHANGELOG.md`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/androidTest/java/eu/kanade/translation/detection/DbnetProcessDeathTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/eu/kanade/translation/detection/DbnetCrashTestService.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/App.kt`
- `app/src/main/java/eu/kanade/translation/ChapterTranslator.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetClient.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetGeometry.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetLease.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetModelStore.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetNativeBackend.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetNotification.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetPaddleRecognizer.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetPixels.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetPostprocessor.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetProcess.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetRecognitionBatch.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetReply.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetService.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetSession.kt`
- `app/src/main/java/eu/kanade/translation/detection/DbnetWire.kt`
- `app/src/main/java/eu/kanade/translation/detection/ExperimentalDbnetOcrEngine.kt`
- `app/src/main/java/eu/kanade/translation/detection/ExperimentalDetectionRoute.kt`
- `app/src/main/java/eu/kanade/translation/detection/TextDetection.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetGeometryTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetLeaseTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetModelStoreTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetNotificationTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetPixelsTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetPostprocessorTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetPreferencesTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetRecognitionBatchTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetReplyTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetSessionTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/DbnetWireTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/ExperimentalDetectionRouteTest.kt`
- `app/src/test/java/eu/kanade/translation/detection/TextDetectionTest.kt`
- `dbnet-native/build.gradle.kts`
- `dbnet-native/src/main/AndroidManifest.xml`
- `dbnet-native/src/main/assets/dbnet-licenses/GPL-3.0.txt`
- `dbnet-native/src/main/assets/dbnet-licenses/NDK-NOTICE.txt`
- `dbnet-native/src/main/assets/dbnet-licenses/NOTICE.txt`
- `dbnet-native/src/main/assets/dbnet-licenses/glslang-LICENSE.txt`
- `dbnet-native/src/main/assets/dbnet-licenses/ncnn-BSD-3-Clause.txt`
- `dbnet-native/src/main/cpp/CMakeLists.txt`
- `dbnet-native/src/main/cpp/buffer_contract.h`
- `dbnet-native/src/main/cpp/dbnet_jni.cpp`
- `dbnet-native/src/test/cpp/buffer_contract_test.cpp`
- `docs/superpowers/plans/2026-08-31-y19-yakuyomi-dbnet.md`
- `docs/superpowers/specs/2026-08-31-y19-yakuyomi-dbnet-design.md`
- `docs/y19-dbnet-execution.md`
- `docs/y19-dbnet-preflight-status.md`
- `docs/yakuyomi-dbnet-upstream.md`
- `domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt`
- `scripts/test-dbnet-core.sh`
- `scripts/verify-dbnet-apk.py`
- `scripts/verify-dbnet-scope.py`
- `settings.gradle.kts`
