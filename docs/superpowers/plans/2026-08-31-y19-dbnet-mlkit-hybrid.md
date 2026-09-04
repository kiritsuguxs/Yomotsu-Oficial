# Y19 DBNet + ML Kit Hybrid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a safer and faster experimental DBNet path that groups DBNet lines, recognizes the full page once with ML Kit, preserves a bounded DBNet text mask for cleanup, and then returns to the existing Y17 translation/render pipeline.

**Architecture:** Keep the existing isolated ARM64 DBNet worker and persistent native session. Extend its result with a bounded mask, group line detections before translation, associate one full-page ML Kit result with grouped DBNet geometry, and expose an optional cleanup mask without changing normal OCR or Y17 rendering behavior. Any invalid/ambiguous experimental page fails atomically to the existing selected OCR.

**Tech Stack:** Kotlin/Android, ML Kit Text Recognition, NCNN/JNI DBNet worker, coroutines/Messenger IPC, existing Y17 translation/cleanup/render stack, JUnit, Android instrumentation tests, native sanitizer checks.

**Spec:** `docs/superpowers/specs/2026-08-31-y19-dbnet-mlkit-hybrid-design.md`

## Global Constraints
- Work only on `agent/y19-dbnet-mlkit-test1`, based on Y19 DBNet baseline `b9d20f16f140710a09076ea7d794b062224f8041`.
- Do not modify `yomotsu-independent`.
- No PR, merge, release, or official promotion.
- Preserve applicationId, signing/update compatibility, translators, translation memory, cache, glossary, Y17 Bubble fitting/rendering, and normal ML Kit/Paddle OCR choices.
- DBNet remains experimental, ARM64/English gated, disabled by default, with its ~153 MB weights outside the APK.
- Paddle remains available as an ordinary OCR engine; it is not the recognizer for this experimental DBNet hybrid.
- Experimental failures fall back for the whole page; never combine partial experimental blocks with fallback output.
- No AOT-GAN and no Yakuyomi renderer.

---

### Task 1: Bounded DBNet mask transport

**Files:**
- Modify: `app/src/main/java/eu/kanade/translation/detection/DbnetPostprocessor.kt`
- Modify: `app/src/main/java/eu/kanade/translation/detection/DbnetWire.kt`
- Modify: `app/src/main/java/eu/kanade/translation/detection/DbnetService.kt`
- Modify: `app/src/main/java/eu/kanade/translation/detection/DbnetClient.kt`
- Test: corresponding DBNet wire/postprocessor unit tests under `app/src/test/.../translation/detection/`

**Interfaces:**
- Produce `DetectionResult.Success` containing original page dimensions, line regions, mask dimensions, and bounded binary mask bytes.
- `DbnetWire` must validate dimensions and encoded byte count before allocation/decoding.

- [ ] Write failing tests proving a valid mask survives encode/decode and malformed/oversized mask metadata is rejected.
- [ ] Run the focused DBNet wire/postprocessor tests and confirm the new assertions fail before production changes.
- [ ] Extend the detection result/postprocessor to retain the native segmentation mask as a compact binary representation rather than discarding it.
- [ ] Extend service/client IPC with strict bounds; do not send an Android Bitmap through Messenger.
- [ ] Run focused tests and existing worker-death/IPC tests; confirm PASS.
- [ ] Commit only mask transport changes with message `feat: preserve bounded DBNet text mask`.

### Task 2: Yakuyomi-style DBNet line grouping

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/detection/DbnetLineGrouping.kt`
- Test: `app/src/test/java/eu/kanade/translation/detection/DbnetLineGroupingTest.kt`

**Interfaces:**
- Consume original-page `TextRegion` line quads from DBNet.
- Produce ordered `DbnetTextGroup` values containing member lines, union/oriented geometry, direction, and angle.

- [ ] Write failing tests: two lines in one speech bubble merge; two nearby bubbles remain separate; a large-gap MST edge splits; rotated compatible lines merge; output reading order is stable.
- [ ] Run `DbnetLineGroupingTest` and verify failures before implementation.
- [ ] Implement only the required Houri/Yakuyomi grouping concepts: permissive geometry linking followed by MST gap-outlier splitting and deterministic ordering. Preserve GPL/source attribution already required by the experiment.
- [ ] Run grouping tests and the existing DBNet geometry/postprocessor suite; confirm PASS.
- [ ] Commit with message `feat: group DBNet lines before recognition`.

### Task 3: Full-page ML Kit association

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/detection/DbnetMlKitAssociation.kt`
- Modify: `app/src/main/java/eu/kanade/translation/ocr/ExperimentalDbnetOcrEngine.kt`
- Test: `app/src/test/java/eu/kanade/translation/detection/DbnetMlKitAssociationTest.kt`
- Test: existing experimental route/fallback tests.

**Interfaces:**
- Consume grouped DBNet geometry and the `OcrPage` produced by the existing selected ML Kit engine for the original full page.
- Produce one deduplicated coherent `OcrPage` in original page coordinates plus group-to-block association metadata needed by cleanup.

- [ ] Write failing tests for one ML Kit block matching multiple DBNet lines, multiple compatible ML Kit lines matching one group, adjacent groups staying separate, unmatched noise being rejected, no duplicate emitted blocks, and ambiguous/no-usable association triggering experimental failure.
- [ ] Run association/route tests and verify RED.
- [ ] Replace `DbnetPaddleRecognizer` use in the experimental route with one call to the existing full-page ML Kit OCR path, then geometrically associate its blocks to DBNet groups.
- [ ] Ensure any association failure occurs before translation side effects and invokes whole-page fallback only once.
- [ ] Run association, ML Kit, route and fallback tests; confirm PASS.
- [ ] Commit with message `feat: pair DBNet detection with full-page ML Kit`.

### Task 4: Constrained cleanup mask adapter

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/detection/DbnetCleanupMask.kt`
- Modify only the smallest existing Y17 cleanup data/interface needed to accept an optional experimental mask.
- Test: `app/src/test/java/eu/kanade/translation/detection/DbnetCleanupMaskTest.kt`
- Test: relevant existing Y17 cleanup/Bubble tests.

**Interfaces:**
- Consume original-size DBNet binary mask and only successfully associated/translated grouped regions.
- Produce an optional original-page cleanup mask clipped to those regions and lightly dilated.
- When absent, Y17 cleanup behavior is byte-for-byte/behaviorally unchanged.

- [ ] Write failing tests proving mask pixels outside translated regions are removed, inside glyph pixels remain, dilation cannot escape the permitted expanded region, invalid dimensions are rejected, and `null` mask preserves legacy cleanup behavior.
- [ ] Run focused tests and verify RED.
- [ ] Implement mask intersection + bounded small dilation and thread the optional mask through the narrowest Y17 cleanup interface possible.
- [ ] Do not replace Y17 Bubble fitting/rendering and do not add AOT-GAN/BoxFill renderer code.
- [ ] Run cleanup tests plus the full existing Y17 Bubble/render regression suite; confirm PASS.
- [ ] Commit with message `feat: constrain Y17 cleanup with DBNet mask`.

### Task 5: Stage timing and atomic fallback diagnostics

**Files:**
- Modify: `ExperimentalDbnetOcrEngine.kt`
- Modify the smallest timing/metrics model used by current OCR logs if required.
- Test: experimental route metrics/fallback tests.

**Interfaces:**
- Record monotonic durations for DBNet request, grouping, ML Kit, association, and mask preparation; total experimental OCR duration remains available.
- Logs must not include recognized/translated text or API credentials.

- [ ] Write failing tests for stage timing bookkeeping and for one atomic fallback when any experimental stage throws/fails.
- [ ] Run focused tests and verify RED.
- [ ] Add monotonic stage timers and a single structured `YomotsuDBNet` timing log per page.
- [ ] Verify failure still closes/disables the failed experimental session as designed and returns the existing OCR page once.
- [ ] Run tests and confirm PASS.
- [ ] Commit with message `perf: trace DBNet hybrid OCR stages`.

### Task 6: Remove obsolete experimental Paddle coupling

**Files:**
- Delete or detach from experimental route only: `DbnetPaddleRecognizer.kt`, `DbnetRecognitionBatch.kt`, and tests that exist solely for DBNet+Paddle, if no other production caller remains.
- Do not remove normal Paddle OCR/model support.

**Interfaces:**
- Experimental DBNet path has no dependency on Paddle recognition/OpenCV crops.
- Normal user-selectable Paddle OCR remains unchanged.

- [ ] Search production references and prove the DBNet-specific Paddle adapter has no remaining caller.
- [ ] Add/adjust a dependency-level regression test if needed to prove the experimental route uses the supplied full-page OCR engine exactly once.
- [ ] Remove only dead DBNet-specific Paddle adapter code.
- [ ] Run all translation/OCR unit tests; confirm PASS.
- [ ] Commit with message `refactor: drop DBNet-specific Paddle crop OCR`.

### Task 7: Full verification and ARM64 artifact

**Files:**
- Update: `docs/superpowers/specs/2026-08-31-y19-dbnet-mlkit-hybrid-design.md` only if implementation evidence requires a factual correction.
- Update: `YOMOTSU_CHANGELOG.md` with an experimental, non-official Y19 test note only if existing project convention records temporary test builds there.

- [ ] Run the complete unit suite and record exact test count/results.
- [ ] Run Android worker-death/instrumentation coverage used by Y19 baseline.
- [ ] Run existing native C++ sanitizer checks.
- [ ] Verify no DBNet weights were bundled into the APK.
- [ ] Verify applicationId/signing identity/update compatibility are unchanged.
- [ ] Build the signed ARM64 experimental APK with the existing workflow.
- [ ] Compare APK bytes against the 116.86 MB Y19 DBNet+Paddle baseline and record the delta.
- [ ] Inspect Action jobs/logs and do not claim success unless every required check is green.
- [ ] Commit verification notes only if repository convention requires them; do not merge, release, or open a PR.

## Phone acceptance
The build remains experimental until manual phone testing. Test the same problematic chapter/pages that showed retained English and PT/EN overlap, plus one timing run. Record per-stage timing from logs if available. Success means materially fewer missed/residual source-text fragments, no new adjacent-bubble merges/duplicate overlays, normal Y17 behavior when DBNet is disabled, and a measurable speed improvement over the ~10-minute/67-page DBNet+Paddle baseline. A one-minute chapter is an optimization target, not an acceptance promise for this iteration.
