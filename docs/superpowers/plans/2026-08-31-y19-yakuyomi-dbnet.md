# Y19 Yakuyomi DBNet Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a removable experimental DBNet detector path from Houri/Yakuyomi for direct phone comparison with Yomotsu Y17 detection.

**Architecture:** Pin and inventory the Houri/Houri-engine detector implementation first, then expose only DBNet through a Yomotsu-owned adapter. Keep current OCR, translation, storage, Bubble cleanup/grouping, and renderer behavior intact; DBNet returns geometry only and fails back safely.

**Tech Stack:** Kotlin, Android, JNI/NCNN as required by the pinned Houri engine, JUnit, Gradle Android build.

**Spec:** `docs/superpowers/specs/2026-08-31-y19-yakuyomi-dbnet-design.md`

## Global Constraints
- Work only on `agent/y19-yakuyomi-dbnet-test1` based on `yomotsu-independent`.
- No PR, merge, release, applicationId/signature/updater change.
- Do not replace or modify ML Kit/PaddleOCR recognition, translators, memory, cache, glossary, Bubble cleanup, Y17 grouping/AutoFit, fonts, or auto-scroll.
- Do not add AOT-GAN, Yakuyomi translator, Yakuyomi cache/storage, renderer/typesetter, or cross-page scheduling in Y19 DBNet stage.
- Pin exact upstream revisions and preserve required license/attribution notices.

## Approved resumption

On 2026-08-31 the user approved the separate-process DBNet service and an additive Paddle recognition-by-regions adapter. The initial stop report is historical. Implement the amendment in the design above; no new approval is required for these two boundaries. Do not change existing OCR algorithms or the default selector values.

### Revised execution units for Tasks 2–5

1. **Geometry** — `app/.../translation/detection/TextDetection.kt`, `DbnetGeometry.kt`, `DbnetPostprocessor.kt`; tests under the matching test package. Contract: `DetectionPoint(x,y)`, immutable `TextRegion` quad/confidence, `DetectionResult.Success(width,height,regions)` or `Failure(reason)`. Validate finite coordinates, positive image dimensions, clipping/degeneracy, deterministic order, independent output-grid scale and padding. Preprocessing size is 1024 with 256 padding. Tests must assert output coordinates and failure values, not source strings.
2. **External model store** — `detection/DbnetModelStore.kt` and tests. Fixed pair from inventory; injectable stream source for tests. Stream both hash and download using bounded buffers, reject short/long/hash-mismatched files, remove partial downloads on interruption, serialize updates, reuse valid files, and never fetch for the default path. Expose `ensureAvailable(): model directory` to the experimental wrapper only.
3. **Native detector** — a `dbnet-native` Android library with detector-only JNI/CMake and guarded `DbnetNative` Kotlin bridge. Add actual-dimension metadata for both outputs; reject invalid JNI lengths/dimensions/float layouts. Catch managed loading failure; process isolation handles fatal native faults. Reuse only inventory-listed NCNN artifacts or an equivalently pinned NCNN source build; keep weights external.
4. **Worker/IPC** — `DbnetService.kt`, `DbnetClient.kt`, and a small process-role helper. Manifest non-exported `:dbnet` service; minimal `App.onCreate` guard. Test disconnect, timeout, cancellation, invalid/oversized reply and native load/inference errors. IPC image input uses read-only file descriptors; never transport page bitmaps or model arrays in a Bundle. Each request returns neutral geometry only; no app state writes in worker.
5. **Recognition and selection** — additive `DbnetPaddleRecognizer.kt`, `ExperimentalDbnetOcrEngine.kt`, pure routing/fallback tests; tiny integration changes in `ChapterTranslator`, `TranslationPreferences`, and settings. Reuse Paddle crop/recognition/mapper and unchanged downstream grouping. Explicit switch warning about first-use model download. Test switch false does not instantiate detector/download, unsupported language/ABI and failures invoke the selected existing engine, success bypasses old detection, and cancellation is not converted to fallback.
6. **Verification** — extend the temporary-branch build to run detector tests plus current tests/checks and build the signed ARM64 APK. Add Android worker-death coverage where runner permits. Verify no DBNet weights in APK; measure actual ARM64 APK delta against baseline. Report any unexecuted Android/device tests explicitly, never as passing. Review all protected-file differences against `1eaf6ea959fa31f2ed50c6fbc05486efa103d0e9`.

Each unit follows tests → observed expected RED → minimum implementation → GREEN. A compiler/download failure is not a behavior-test RED result. Commit evidence in a concise execution log. Phone acceptance remains with the user.

---

### Task 1: Upstream detector inventory and boundary

**Files:**
- Create: `docs/yakuyomi-dbnet-upstream.md`
- No production changes.

**Interfaces:**
- Consumes: pinned Houri revision and its `external/yakuyomi-engine`/houri-engine revision.
- Produces: exact detector source/model/dependency inventory and the native-to-Kotlin call signature needed by Task 2.

- [ ] Record exact Houri and houri-engine SHAs and detector-related source paths.
- [ ] Record DBNet model files, sizes, hashes where practical, NCNN/JNI/native libraries, ABIs, build flags, and license/notice requirements.
- [ ] Trace the call from Kotlin page input to DBNet inference output and document coordinate semantics.
- [ ] Explicitly mark OCR, AOT-GAN/inpainting, translation, renderer, cache/storage, and scheduling files as out of scope.
- [ ] Commit documentation only.

### Task 2: Neutral detector geometry contract

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/detection/TextDetection.kt`
- Create: `app/src/test/java/eu/kanade/translation/detection/TextDetectionTest.kt`

**Interfaces:**
- Produces: Yomotsu-owned immutable detection geometry and normalization helpers independent of Houri types.

- [ ] Write failing tests for normal boxes, clipped boxes, invalid/empty geometry, deterministic reading/order preservation, and scale conversion.
- [ ] Run focused tests and confirm RED for missing contract/normalization behavior.
- [ ] Implement the smallest neutral geometry contract and normalization helpers.
- [ ] Run focused tests and require GREEN.
- [ ] Commit contract + tests.

### Task 3: DBNet adapter and native/model integration

**Files:**
- Create/modify only detector-specific Kotlin/native/build files identified by Task 1.
- Test: detector adapter unit/instrumentation tests as appropriate.

**Interfaces:**
- Consumes: page image plus pinned DBNet model/native runtime.
- Produces: Task 2 neutral detection geometry; no translated text or rendered output.

- [ ] Write failing adapter tests around output conversion and initialization/inference failure behavior before production adapter code.
- [ ] Verify RED.
- [ ] Port/adapt the minimum upstream DBNet detector/native bridge and model-loading pieces; do not copy the complete Yakuyomi pipeline.
- [ ] Convert native detector output immediately into Yomotsu neutral geometry.
- [ ] Ensure missing/corrupt model or native initialization failure returns an explicit recoverable detector failure and leaves existing OCR usable.
- [ ] Run focused tests and require GREEN.
- [ ] Commit DBNet adapter/native/model integration.

### Task 4: Experimental selection without replacing Y17

**Files:**
- Modify only the smallest existing OCR/detection orchestration/preferences files identified during implementation.
- Test corresponding orchestration/preferences behavior.

**Interfaces:**
- Existing ML Kit and PaddleOCR paths remain behaviorally unchanged.
- Experimental DBNet selection invokes Task 3 detector and feeds geometry into the existing downstream path only where compatible.

- [ ] Write failing tests proving existing defaults are unchanged and DBNet is opt-in/experimental.
- [ ] Verify RED.
- [ ] Add the smallest selection/wiring needed for phone testing; no storage schema migration.
- [ ] On DBNet failure, preserve a usable existing OCR path and surface a clear error/fallback state.
- [ ] Run focused tests and require GREEN.
- [ ] Commit experimental wiring.

### Task 5: Regression/build verification

**Files:**
- No production changes unless a test demonstrates an integration defect within Y19 scope.

- [ ] Run all detector-focused tests.
- [ ] Run existing translation model/presentation/provider regression tests used by CI.
- [ ] Run the repository's normal unit-test/check target.
- [ ] Run the supported Android debug/build target and require success.
- [ ] Compare `agent/y19-yakuyomi-dbnet-test1` against `yomotsu-independent`; verify no unexpected translator, memory/cache/glossary, Bubble renderer/cleanup, identity/signing/updater, font, auto-scroll, or theme changes.
- [ ] Record APK/model size delta and stop on the temporary branch for phone testing. Do not merge or release.

### Task 6: Phone acceptance

- [ ] Test identical known-problem pages with current Y17 detector path and experimental DBNet.
- [ ] Record missed text and false-positive differences.
- [ ] Check small text, dark/complex backgrounds, and neighboring balloons.
- [ ] Compare processing time and app stability on the phone.
- [ ] Compare storage/model-size increase.
- [ ] Keep DBNet only after explicit user approval; otherwise discard the temporary branch.
