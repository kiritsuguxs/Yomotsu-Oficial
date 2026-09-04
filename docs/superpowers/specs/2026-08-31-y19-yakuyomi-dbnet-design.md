# Y19 Yakuyomi DBNet Detection Design

## Goal
Evaluate Houri/Yakuyomi's DBNet text detector inside Yomotsu without replacing the existing translation stack or changing the approved Y17 behavior.

## Scope
- Temporary branch only: `agent/y19-yakuyomi-dbnet-test1`, based on `yomotsu-independent`.
- Integrate only the minimum DBNet detection path required for a phone comparison.
- Keep ML Kit and PaddleOCR available and unchanged as existing paths/fallbacks.
- Do not integrate AOT-GAN, Yakuyomi translation providers, typesetter/renderer, cache, storage, or cross-page scheduling in this stage.
- Do not change applicationId, signing, updater, release workflow, translation memory, glossary, cache, providers, Bubble cleanup, or Y17 AutoFit/grouping behavior.
- No PR, merge, or release until the phone test is approved.

## Source boundary
Houri's app repository exposes a Kotlin integration module and points its native engine to the separate `PineappleTwilight/houri-engine` submodule. The implementation stage must pin the exact Houri/Houri-engine revision used and inventory all copied/adapted source, JNI/NCNN dependencies, model files, and license notices before adding production code.

## Architecture
Introduce DBNet behind a small detector adapter boundary rather than wiring Houri's complete translation engine into Yomotsu. The adapter should accept a page bitmap/image representation and return detector geometry in a Yomotsu-owned neutral structure. Existing Yomotsu OCR/translation/rendering remains downstream and unchanged unless a strictly necessary coordinate conversion is required.

The experimental DBNet path must fail safely: model initialization or native inference failure must not corrupt chapter state. Existing OCR remains usable, and the experiment must be removable by deleting the adapter/integration without migrating stored translation data.

## Validation
Use TDD for adapter/geometry behavior. Verify coordinate conversion, empty detections, out-of-bounds boxes, model/native initialization failure, and stable ordering. Run focused unit tests, the existing translation test suite, and the repository's Android debug build/CI target. Compare the branch against `yomotsu-independent` to ensure protected translation/provider/rendering/identity files did not change unexpectedly.

## Phone acceptance test
Test the same pages where Y17 misses text, especially small speech, dark/complex backgrounds, and nearby balloons. Compare detection coverage, false positives, translation placement, processing time, app memory/stability, and additional model/storage size. DBNet is retained only if the detection gain justifies its device cost.

## Approved architecture amendment (2026-08-31)

The user approved resuming after the documented architectural stop: a separate DBNet process and an additive recognition-by-regions adapter. This approval does not authorize PR, merge, release, official promotion, or changes to existing default behavior.

- Add a non-exported bound Android service in `:dbnet`. Only this process loads NCNN. Skip normal app initialization in this worker so it does not open the app database, run migrations, or initialize translation/cache/updater services.
- The main process owns external model download and verifies frozen SHA-256/size using streaming and temporary files followed by atomic replacement. Download only on the first explicit experimental use, with a clear settings warning (153 MB), never during normal OCR or app startup. Store under the app's internal models directory; never bundle weights.
- Send a read-only page file descriptor through bounded Messenger IPC; return only a bounded list of immutable quads/confidences and original page dimensions. Serialize model initialization/inference/destruction. Handle binding failure, worker death, timeout, invalid response and native errors as recoverable detector failures. Cancellation propagates and does not silently translate the cancelled chapter.
- Retain aspect-preserving resize, right/bottom padding to 256 multiples, RGB CHW `[-1,1]`, sigmoid, components, minimum-area rectangle and unclip. Validate actual DB and mask dimensions and buffer lengths before any native copy. Never interpret fixed half-resolution mask dimensions as universal. No cleanup mask enters Y17.
- Add a separate English-only DBNet OCR adapter using the existing public Paddle `RecognitionEngine`, crop utilities and mapper. Do not modify the existing ML Kit engine, Paddle engine, or vendored Paddle recognition/batching algorithms. DBNet regions go directly to recognition, without a second detector pass.
- Add an experimental switch separate from the existing OCR selector (default false). The selected existing engine is the fallback; unsupported language/ABI, missing/corrupt model, worker failure and unusable recognition fall back visibly. The switch and additive wrapper must not alter default behavior or the existing strict grouping choice.
- Include focused JVM tests and Android IPC/process-death tests. Keep tests honest: local JVM tests cannot certify native ARM64 inference, and a successful build cannot replace the user's phone acceptance.
- Native build integration must remain detector-only, pin runtime provenance and preserve license notices. Do not import the complete Houri engine module or ship OCR/inpainting models.
