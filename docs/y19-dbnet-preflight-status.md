# Y19 DBNet: preflight stopped before production changes

Date: 2026-08-31

**Historical report:** the user subsequently approved the separate-process service and additive recognition adapter. See the approved amendment in the design and plan. The observations and verification below describe the preflight commit, not the resumed implementation status.

## Scope and state

- Work branch: `agent/y19-yakuyomi-dbnet-test1`.
- Initial branch commit: `096b1802a8e17138c353e23ee9c48b55a15e8b00`.
- Official base verified remotely: `1eaf6ea959fa31f2ed50c6fbc05486efa103d0e9` (`yomotsu-independent`).
- The design, implementation plan, and `YOMOTSU_CHANGELOG.md` were read first.
- No DBNet implementation, new preference, model download, production change, PR, merge, or release was performed.
- This is an incomplete experiment. Tasks 2–6 remain pending. No RED/GREEN TDD cycle is claimed.

## Architectural stop: native crash containment

The requested guarantee is that an NCNN failure must not close the app. The pinned upstream invokes NCNN directly in the calling process. Its Kotlin `catch (Throwable)` and integer JNI error returns can handle library-loading errors and reported inference failures, but cannot recover from a native `SIGABRT` or `SIGSEGV` that terminates that process.

Evidence at Houri-engine `85351aa3822fe2611f68cfd092972e6ac573f203`:

- `ImageOps.kt` documents heap corruption/malloc crashes for square inputs in the 832–992 range. Aspect-preserving resize and padding to 256 multiples avoid that known case; they do not contain other native failures.
- `NcnnBackend.kt` documents a device `__kmp_abort_process`/`SIGABRT` from concurrent native inference and uses a global inference lock. The lock must be retained, but it is not process isolation.
- `ncnn_jni.cpp` copies native outputs into Java arrays. Its upper-bound checks and dynamic mask dimensions are essential, but the adaptation still needs explicit Java array-length checks, input/extract return-code checks, layout checks, and synchronized model lifetime management.

These are source-documented failure modes and inspection findings, not crashes reproduced in this session.

Meeting the strict crash-containment requirement calls for a DBNet-only Android service in a separate process, with bounded IPC, cancellation/timeouts, binder-death handling, model lifecycle ownership, and fallback in the surviving caller. The current plan does not specify that boundary. An in-process wrapper must not be presented as providing the same protection.

## Recognition boundary requiring a design decision

The current `OcrEngine` contract is `recognize(OcrImage): OcrPage`. It receives an image and returns text plus coordinates, not geometry awaiting recognition.

- `MlKitOcrEngine.recognize` calls ML Kit's combined detection/recognition operation. This adapter has no entry point accepting externally detected quadrilaterals.
- `PaddleOCR.recognize` calls `OCREngine.run`, whose private image path always invokes `detectionEngine.detect` before sorting, cropping, and recognition. Its existing recognition-only stage is not exposed through the app's OCR contract.
- `ChapterTranslator` requires recognized `OcrTextBlock` text and symbol metrics before the unchanged Bubble/Y17 conversion and grouping stages.

Consequently DBNet geometry cannot simply replace the current `OcrPage`. Cropping each DBNet region and running an existing whole-image OCR engine is possible, but performs another detection pass and can change segmentation, recall, ordering, and symbol metrics. That must be described and tested as a different integration strategy, not silently treated as detection-only replacement.

A narrowly scoped Paddle recognition-only adapter is another possible design: the SDK already exposes `RecognitionEngine.recognize(crops)` and `ORTSessionManager`, so an additive adapter may reuse these without changing the existing OCR engine. The current session manager loads both detector and recognizer models, even when only recognition is needed. The adapter would still need explicit coverage of cropping, recognition/batching, ordering and symbol metrics, and would retain the current English-only restriction. This is a missing integration decision, not proof that a broad rewrite is necessary. Neither approach was implemented or selected on the user's behalf.

## Proposed continuation, not an approved design change

1. Amend the design/plan to define the DBNet process boundary and how caller fallback survives native process death; retain model streaming, pinned SHA-256, dynamic dimensions, and rectangular/padded preprocessing.
2. Decide whether the first phone experiment is geometry-only comparison or translation through a minimal recognition-only adapter. Keep the existing default path byte-for-byte unchanged where possible.
3. Add failing tests before implementing these behaviors. Test process-death fallback on Android as well as JVM geometry/model tests; a mocked Kotlin exception alone does not validate native crash containment.
4. Complete existing regressions, ARM64 APK build, actual APK size comparison, and phone tests before claiming the experiment is ready.

The stop follows the user's explicit instruction to stop and explain an important architectural incompatibility rather than improvise a large rewrite.

## Verification actually performed

Local commands attempted:

```sh
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.translation.recognizer.*' --console=plain
./gradlew assembleRelease -Penable-updater --console=plain
```

Both exited with status 1 before executing tests or compiling code. Gradle wrapper could not download `https://services.gradle.org/distributions/gradle-9.6.1-bin.zip`: `java.net.SocketException: Network is unreachable`. The local environment has Java 17, whereas `.github/.java-version` requests 21; an Android SDK was not found. No build or toolchain configuration was changed to hide the limitation. This is an environment failure, not an expected TDD RED result.

Existing CI inspected: [run 33345437700](https://github.com/kiritsuguxs/Yomotsu/actions/runs/33345437700), commit `096b1802a8e17138c353e23ee9c48b55a15e8b00`. The run completed successfully, including the Y17 regression suite, ARM64 APK build, identity/Paddle asset/signature checks, and artifact upload. It is the original Y17 code with planning documents, **not a DBNet-enabled APK**, and it is not a new run from this session. The complete normal check suite was not newly executed.

The branch diff against the official base contained only the two existing planning documents before this preflight. The final documentation commit must likewise contain only Markdown files under `docs/`; production, tests, build configuration, workflows, identity, signature, updater, OCR, providers, memory, cache, glossary, Bubble/Y17, fonts, and auto-scroll remain unchanged.

## Sizes

- Additional APK code/model payload introduced by this preflight: none. No new APK was generated and no binary-to-binary delta was measured.
- Manifest-declared external `.bin`: 153,010,556 bytes.
- Manifest-declared external `.param`: 13,392 bytes.
- Total separate detector download: 153,023,948 bytes (153.024 MB / approximately 145.935 MiB).
- The weights were not downloaded in this session. Their expected SHA-256 values, URLs, and licensing are recorded in `yakuyomi-dbnet-upstream.md`.
