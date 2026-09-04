# Yomotsu Y13-test1 OCR2 Design

## Context

Yomotsu `0.20.4-Y12` currently performs OCR through Google ML Kit directly inside
`ChapterTranslator`. The recognizer returns ML Kit `Text.TextBlock` objects, so the
chapter pipeline is coupled to one OCR provider even though translation engines are
already selectable.

Y13-test1 introduces a second, offline OCR provider without changing the approved
translation behavior. The implementation lives only on `agent/y13-test1-ocr2` until
the test APK is approved. `yomotsu-independent` remains unchanged.

## Goals

- Introduce a small provider-neutral OCR contract.
- Preserve ML Kit as the default and preserve its current behavior.
- Add PaddleOCR as a manually selected second provider.
- Optimize the PaddleOCR option for English scanlations of manhwa and manhua.
- Add an `OCR` section immediately below `Tradutor` in translation settings.
- Build an installable, signed arm64 test APK with the existing package and signing
  identity.
- Preserve the Y12 translation, glossary, memory, cache, cleanup, layout, manual
  editing, queue, notification, download, and serialization paths after OCR output.

## Non-goals

- Tesseract.
- An automatic OCR mode or automatic provider fallback.
- Combining results from multiple OCR providers.
- Real-time translation or OCR in the reader viewport.
- New source languages for PaddleOCR in this test.
- Changes to translation providers, prompts, glossary rules, translation memory,
  cleanup heuristics, translated CBZ behavior, application ID, or signing keys.
- Merge into `yomotsu-independent` or an official release.

## Selected Approach

Use PaddlePaddle's official Android SDK as a source module, pinned to PaddleOCR commit
`2661c7c0ef5c613e8f93c6e93b2e052399f0f854`. The module uses ONNX Runtime and OpenCV,
has `minSdk 26`, and exposes an end-to-end detector/recognizer API. Yomotsu already
uses `minSdk 26` and Java 17, so no platform floor changes are required.

Source integration is preferred over a prebuilt AAR because it keeps the SDK visible
to review, permits deterministic ProGuard configuration, and allows the Android
module to be compiled with Yomotsu's Gradle setup. Reimplementing PaddleOCR directly
inside `ChapterTranslator` is rejected because it would duplicate the official image
preprocessing and postprocessing code and recreate the coupling this release is
intended to remove.

All imported PaddleOCR source files retain their Apache-2.0 copyright headers. The
repository records the upstream commit and model provenance.

## Bundled Models

The APK bundles these official Apache-2.0 ONNX assets and works offline after
installation:

| Purpose | Official model | Asset size | Archive SHA-256 |
| --- | --- | ---: | --- |
| Text detection | `PP-OCRv5_mobile_det_onnx_infer` | 4,826,518 bytes | `781056046c9ed77a15c94681605db6a0f62317c2e9cce6931c71da2478d4bc30` |
| English recognition | `en_PP-OCRv5_mobile_rec_onnx_infer` | 7,848,423 bytes | `4424e851309b291b00aab8191cd4314cefbd2d1b2381ff8994019d262fa95e28` |

The detector is stored as `models/det/inference.onnx`. The English recognizer and its
official `inference.yml` character configuration are stored below `models/rec/`.
There is no runtime model download, network permission change, or model updater in
Y13-test1.

## OCR Contract

The recognizer package gains provider-neutral models:

- `OcrEngineType`: stable preference values for `ML_KIT` and `PADDLE_OCR`.
- `OcrEngine`: provider contract containing provider type, source language, a
  suspending `recognize` operation, and suspending resource release.
- `OcrPage`: source image dimensions plus recognized blocks.
- `OcrTextBlock`: text, axis-aligned bounds, line angle, representative glyph size,
  and optional confidence.
- `OcrImage`: a local image URI and a controlled way for an engine to open its encoded
  bytes without making the translation pipeline depend on ONNX or OpenCV.
- `OcrEngineFactory`: the only component that maps the saved preference to an engine.

`MlKitOcrEngine` wraps the current Google recognizer. It maps ML Kit blocks into
`OcrTextBlock` using the same block bounds, first-symbol dimensions, line angle, and
text currently consumed by `ChapterTranslator`.

`PaddleOcrEngine` wraps the official `PaddleOCR` SDK. Each quadrilateral is converted
to an axis-aligned bounding rectangle for the existing cleanup/layout pipeline. The
top edge determines the line angle. Representative glyph width is estimated from the
line width and non-whitespace character count; glyph height uses the quadrilateral
height. Empty or one-character results are filtered by the same downstream rule used
by ML Kit rather than by provider-specific translation logic.

The factory performs no fallback. Selecting PaddleOCR always means PaddleOCR, and
selecting ML Kit always means ML Kit.

## Chapter Data Flow

The per-page flow becomes:

1. `ChapterTranslator` writes the downloaded page to its existing temporary file.
2. The selected `OcrEngine` reads that local image and returns `OcrPage`.
3. `ChapterTranslator` converts neutral `OcrTextBlock` values into the existing
   `TranslationBlock` model.
4. The existing speech-bubble analysis, grouping, sound-effect filtering, translation,
   glossary, memory/cache, cleanup geometry, sorting, JSON serialization, notification,
   and queue behavior continue unchanged.

The only intentional change inside `ChapterTranslator` is the OCR construction,
lifecycle, provider/language reinitialization, and input type accepted by
`convertToPageTranslation`. No translator selection or post-OCR business rule moves
into an OCR provider.

The cached engine is recreated when either the configured OCR provider or source
language changes. The old engine is released before replacement. It is also released
when the translation queue is cleared or the current translation session finishes;
pausing preserves it so resume does not reload the ONNX models. Cancellation
continues to propagate through the existing coroutine path.

## Language Behavior

ML Kit keeps all four current source languages: English, Chinese, Japanese, and
Korean.

The Y13-test1 PaddleOCR model is English-only. If PaddleOCR is selected while the
source language preference is not English, queuing a chapter stops before translation
and presents a localized message explaining that this test engine requires English.
It does not silently use ML Kit. This keeps the provider choice explicit and avoids
creating the excluded `Automático` behavior.

## Settings

`Configurações > Traduções` keeps its current ordering through the `Tradutor` group,
then adds:

- Group title: `OCR`
- List preference title: `Mecanismo de OCR`
- Options:
  - `ML Kit (no dispositivo)`
  - `PaddleOCR (otimizado para inglês)`

The default stored value is ML Kit, so existing Y12 installations behave identically
after installing the test APK. Base English strings are added alongside Brazilian
Portuguese strings to keep the resource module complete and searchable.

## Error Handling

- Invalid PaddleOCR/source-language combinations are rejected before work enters the
  translation queue.
- Missing or corrupt bundled models fail the affected chapter through the existing
  translation error notification and log path.
- A page with no recognized blocks remains a valid empty OCR result and does not
  abort the chapter.
- PaddleOCR initialization and inference run on the existing IO/cancellation path.
- There is no silent provider fallback, partial merge, or network retry.

## Compatibility and Versioning

- `applicationId`: `app.mihon.tachiyomiat` (unchanged).
- `versionCode`: `67`.
- `versionName`: `0.20.4-Y13-test1`.
- Signing: unchanged GitHub Actions signing secrets and keystore path.
- Test branch: `agent/y13-test1-ocr2`.
- Base branch and commit: `yomotsu-independent` at
  `76a5bc9f43baf1530ec08988894ca474ed9cfbde`.
- Official workflows and release tags are not invoked.

The existing test-build workflow guard is updated only to recognize version code 67,
version name `0.20.4-Y13-test1`, the unchanged package ID, and the presence of the two
OCR providers. The workflow continues to run the Y12 regression suite before signing
and uploading the arm64 APK artifact.

## Tests and Acceptance Criteria

Automated tests cover:

- Stable preference decoding with ML Kit as the default.
- Explicit selection of PaddleOCR without fallback.
- Paddle quadrilateral-to-neutral-block bounds, angle, and representative glyph
  geometry.
- English-only eligibility for PaddleOCR.
- Existing translation model, memory, glossary, translator, and presentation tests.
- Release compilation, resource shrinking, ProGuard/R8, and APK signature validation.
- Presence of both bundled ONNX models and the English recognition configuration in
  the release APK.

The implementation is accepted for phone testing when:

1. A Y12 installation can install the Y13-test1 APK over itself.
2. ML Kit remains selected by default and a translation still follows the Y12 path.
3. PaddleOCR can be selected manually and completes an English chapter offline.
4. PaddleOCR output reaches the same bubble cleanup and translation pipeline.
5. Non-English PaddleOCR selection displays the explicit compatibility message.
6. The GitHub Actions regression suite, release build, and signature verification pass.
7. The APK is exposed only as a test workflow artifact; no merge or official release
   occurs.

## Upstream References

- PaddleOCR Android deployment:
  <https://www.paddleocr.ai/main/en/version3.x/inference_deployment/cross_platform/android_deployment.html>
- PaddleOCR upstream source:
  <https://github.com/PaddlePaddle/PaddleOCR/tree/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/deploy/ppocr-android>
- Official English PP-OCRv5 Mobile recognition model:
  <https://huggingface.co/PaddlePaddle/en_PP-OCRv5_mobile_rec>
