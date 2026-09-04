# Y19 DBNet upstream inventory and integration boundary

Historical pre-implementation inventory. The approved integration and current
validation results are recorded in [y19-dbnet-execution.md](y19-dbnet-execution.md).

Inventory date: 2026-08-31. This is Task 1 documentation only. No upstream production code, native library, model weight, or recognition change is introduced by this document. The experiment remains on `agent/y19-yakuyomi-dbnet-test1`; there is no PR, merge, or release; only documentation is prepared for a commit on the temporary branch.

**Architecture finding:** the pinned engine exposes a useful detector boundary, but it is not a drop-in detector replacement in Yomotsu. Yomotsu currently accepts whole images through combined detection/recognition APIs, and the upstream native inference implementation cannot contain a native process crash with Kotlin exception handling. Both issues need resolution before the production integration described in Tasks 3–4. A geometry-only debug comparison would be a different, narrower experiment and must be described as such.

## Exact upstream references

| Source | Pinned revision | Evidence and role |
|---|---|---|
| [PineappleTwilight/Houri](https://github.com/PineappleTwilight/Houri) | `dc2b4af3ed3c4374849cbd9b7964db051a464a25` | The GitHub contents API verified the `external/yakuyomi-engine` gitlink against the engine revision below. |
| [PineappleTwilight/houri-engine](https://github.com/PineappleTwilight/houri-engine) | `85351aa3822fe2611f68cfd092972e6ac573f203` | Local `git rev-parse HEAD` and origin verified; commit dated 2026-08-29. All engine paths and hashes below refer to this revision. |
| [zyddnys/manga-image-translator](https://github.com/zyddnys/manga-image-translator) | Export/source comments identify short revision `d5a3eee` | Behavioral/model ancestor, not a separately vendored dependency in Y19. Full source SHA is not established by this inventory. |

The engine's `.upstream-ref` currently says `95227a2`, whereas `docs/BUILD_MODELS.md` still describes an `efdc229` pin and a `d5a3eee` build. The detector source and exporter identify `d5a3eee`. These are distinct provenance claims; do not silently present them as the same revision. The exact engine revision and released model hashes are the reproducible integration pins for this experiment. Exporting again would require resolving the full model-source revision separately.

## Minimum detector source surface

Engine Kotlin paths below are relative to `engine/src/main/kotlin/li/joye/yakuyomi/engine/`.

| Source | Detector role | Boundary decision |
|---|---|---|
| `Detector.kt` | Load DBNet; invoke inference; sigmoid; components; scoring; quadrilaterals | Adapt only geometry detection. Its full-page `textMask` allocation and mask conversion serve inpainting and are outside Y19 output. |
| `ImageOps.kt` | Resize/pad, RGB CHW normalization, optional unsharp filter | Preserve detector input semantics and bounded dimensions. Default sharpening is off. |
| `Geometry.kt` | `Pt`, `RotRect`, hull, minimum-area rectangle, rectangular unclip | Only detector primitives; omit grouping-specific polygon distance/area helpers and OCR padding behavior unless independently needed. |
| `Config.kt` | `DetectorConfig` defaults | Only detector fields; no engine/OCR/inpainting/renderer settings. |
| `NcnnBackend.kt` | Library availability, native model handle, serialized inference | Requires a Yomotsu-owned detector-only bridge and safe lifecycle. Omit `inpaintAot` and upstream pipeline tracing dependencies. |
| `TextLine.kt` | Quad plus confidence, later mutated by OCR/translation | Reference only. Return immutable Yomotsu geometry immediately; do not expose this mutable text-bearing upstream type. |
| `engine/src/main/cpp/ncnn_jni.cpp` | `createNet`, `releaseNet`, `detectDbnetNative` | Detector JNI subset only. Requires explicit validation changes; do not copy `inpaintAotNative`. |
| `engine/src/main/cpp/CMakeLists.txt` | Build `libyakuyomi_ncnn.so` from bridge and static NCNN | Adapt detector-only target/name and preserve JNI naming or register natives explicitly. |
| `engine/src/main/cpp/ncnn/` | Prebuilt NCNN/glslang archives, headers, CMake target metadata | Required native dependency surface for this upstream build; inventory below. |
| `engine/build.gradle.kts`, `engine/consumer-rules.pro` | ABI/NDK/CMake, native symbol keep rules | Reference only; do not import the complete engine Gradle module. |
| `models.json`, `docs/MODELS.md`, `docs/BUILD_MODELS.md` | Distribution pins and reconstruction provenance | Freeze detector entries only, independent of mutable online manifest. |
| `parity/export_dbnet_ncnn.py`, `parity/paths.py`, `parity/requirements.txt` | Checkpoint-to-NCNN export reference | Audit/rebuild tooling only; not Android dependencies. |

The full engine Gradle module also declares ONNX Runtime Android 1.20.0, coroutines Android 1.11.0, and OkHttp 5.3.2. Those are dependencies of the full engine and must not be imported just to get DBNet. Detector computation itself uses Android graphics, Kotlin/JVM, JNI, and NCNN. There is no OpenCV, Python, PyTorch, ONNX, OCR alphabet, or cloud dependency in the detector runtime.

## Model files and provenance

The engine checkout does not ship the DBNet weights. **No model weights were downloaded or added during this inventory.** The following are canonical expected values from the pinned `models.json`, not hashes measured from locally downloaded model files.

| File | Bytes | SHA-256 |
|---|---:|---|
| `dbnet_detect.ncnn.param` | 13,392 | `9e6db2f8c6b0662ab00eb2100b3373d3c984a235eaac0e61c0b2a484ee1ff7b5` |
| `dbnet_detect.ncnn.bin` | 153,010,556 | `f57bdbede7764a534c56e88be0269602259a7fcd47e54e8b7d954fd0fcc55c3d` |

Canonical release URLs recorded by that manifest:

- [DBNet param](https://github.com/joyeli/yakuyomi-engine/releases/download/models-v3/dbnet_detect.ncnn.param)
- [DBNet bin](https://github.com/joyeli/yakuyomi-engine/releases/download/models-v3/dbnet_detect.ncnn.bin)

Pair total: **153,023,948 bytes** (about 145.94 MiB), excluding native runtime, inference buffers, and working weights. The detector is DBNet with ResNet34 plus DB head, converted through PyTorch trace and pnnx, retaining fp16 weight storage. Upstream reports int8 produces no boxes and is not used. These are not an APK-size measurement or independently reproduced performance results.

The original checkpoint is `detect-20241225.ckpt`, declared size 308,380,176 bytes, expected SHA-256 `67ce1c4ed4793860f038c71189ba9630a7756f7683b1ee5afb69ca0687dc502e`, from [manga-image-translator beta-0.3](https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/detect-20241225.ckpt). The exporter records torch 2.1.1, torchvision 0.16.1, pnnx 1.0.20260526, and desktop verification NCNN 1.0.20260526, with trace `[1,3,1024,768]`. Export-time NCNN is not the bundled Android runtime version.

`ModelDownloader.DEFAULT_MANIFEST_URL` points to mutable `joyeli/yakuyomi-engine/main/models.json`. Copying that default would defeat this experiment's pin. `ModelSet.resolve` requires OCR, detector, and inpainting roles together, and the full downloader processes every role; neither belongs in detector-only integration. Any future detector loader must verify the two frozen expected sizes/hashes before native parsing and keep weights external. Missing or mismatched files must produce a recoverable unavailable/failure result. Never substitute an unchecked local `.param` merely because its filename matches.

## Native dependency and build inventory

| Item | Pinned checkout evidence |
|---|---|
| Android ABI | Only `arm64-v8a` in engine `abiFilters`; NCNN archive first object is AArch64 ELF (`e_machine=183`). No x86/x86_64/armeabi-v7a binaries are provided. |
| NCNN version | `1.0.20260718` / `20260718` in bundled `platform.h` and `ncnnConfig.cmake`; exact NCNN source commit and build recipe are not recorded here. |
| glslang version | `16.1.0` in bundled `glslang/build_info.h`; exact source commit not recorded. |
| Android toolchain | Upstream minSdk 26, compileSdk 37, NDK `28.2.13676358`, CMake `3.22.1`, Java/JVM 17, AGP 9.3.2. These are upstream requirements, not instructions to upgrade Yomotsu wholesale. |
| C++ | C++17; `-DANDROID_STL=c++_static`; imported NCNN interface propagates `-fno-rtti` and `-fno-exceptions`; position-independent static archive. |
| Native output | `libyakuyomi_ncnn.so`, built from `ncnn_jni.cpp`; no prebuilt final JNI `.so` in this checkout. |
| Link surface | `ncnn`, `log`; imported NCNN adds Threads, `dl`, `glslang::glslang`, `glslang::SPIRV`, `android`, `jnigraphics`, `log`. |
| Enabled NCNN features | `NCNN_THREADS`, `NCNN_VULKAN`, `NCNN_SIMPLEVK`, `NCNN_RUNTIME_CPU`, ARM feature dispatch, int8 and bf16 support. Model execution uses CPU; upstream bridge does not enable Vulkan compute. |
| OpenMP nuance | CMake metadata sets `NCNN_OPENMP=ON`, but `platform.h` sets `NCNN_SIMPLEOMP=1`; archive contains `simpleomp.cpp.o` and defines OpenMP/KMP ABI functions. The target link interface does not itself list `OpenMP::OpenMP_CXX`. Do not assume the old bridge comment's standalone static `libomp` describes this exact archive. |
| Shrinking/JNI | Upstream `consumer-rules.pro` keeps `NcnnBackend` and native method names. A renamed Yomotsu bridge requires matching exported names/registration and keep rules. Do not import ONNX-specific keep rules solely for DBNet. |

Bundled static archives, locally measured at the engine pin:

| Archive | Bytes | SHA-256 |
|---|---:|---|
| `libGenericCodeGen.a` | 1,324 | `9d477f2cbd65ef16281abf3e64fa9f3f52b6c4bfc62f7692753759fcf432947a` |
| `libMachineIndependent.a` | 1,324 | `9d477f2cbd65ef16281abf3e64fa9f3f52b6c4bfc62f7692753759fcf432947a` |
| `libOSDependent.a` | 1,324 | `9d477f2cbd65ef16281abf3e64fa9f3f52b6c4bfc62f7692753759fcf432947a` |
| `libSPIRV.a` | 1,324 | `9d477f2cbd65ef16281abf3e64fa9f3f52b6c4bfc62f7692753759fcf432947a` |
| `libglslang-default-resource-limits.a` | 51,858 | `0e7228b204413c1effaf9a30e854960831627de244cdd2b0e34e259c1698b717` |
| `libglslang.a` | 6,276,710 | `014f2e540caacd97db57a98efd826dbdd9503d391891c8d8a7ffe0f10de43264` |
| `libncnn.a` | 10,103,784 | `28cfbe206befd03540322df0fe5d31f819661add1c373f8e77e16f7d5f9a0436` |

The archive files total 16,437,648 bytes before final linking/stripping. This does not predict installed `.so` or APK delta. Native packaging alignment (including 16 KiB page support), final symbol resolution, supported-device behavior, and memory use must be checked on the actual Android build; this inventory did not build or run it.

## Call boundary and coordinates

The engine's public whole-pipeline route is `Yakuyomi.create(...)` → `TranslationEngine.translatePage(Bitmap)` → `Pipeline.translatePage` → `Detector.detect(page)`. Creation also loads upstream OCR and AOT-GAN, so Y19 must not use that factory. A standalone detector can instead be constructed directly from the pinned `.param` path (the `.bin` path is derived by replacing the suffix).

The detector route is:

1. `Detector.detect(Bitmap)` calls `ImageOps.detectorChwDbnet(page, 1024, false)`.
2. Compute `ratio = 1024 / max(originalWidth, originalHeight)`. Round each resized edge with `roundToInt`, clamping to at least one pixel. Bilinearly resize; place at `(0,0)` and pad **right/bottom only** to multiples of 256 with black RGB. Square pages can still produce 1024×1024; it is the 256-multiple dimension policy that avoids the reported 832–992 square crash band.
3. Convert pixels to RGB planar CHW floats using `channel / 127.5f - 1f`. Array index is `channel * width * height + y * width + x`.
4. Serialized Kotlin bridge calls the JNI function below. NCNN creates `Mat(inW,inH,3)`, inputs blob `in0`, extracts `out0` and `out1`.
5. Expected `out0` is two channels at full input resolution. Channel 0 contains raw shrink-map logits; apply sigmoid once. Channel 1 is unused for geometry. `out1` is an already-sigmoid stroke mask, reported by upstream as half resolution on x86 and full resolution on ARM; it does not determine boxes.
6. Iterate probability grid in row-major seed order. An eight-connected component consists of probabilities strictly greater than 0.5. Score is the component's mean probability; discard score below 0.7. Find a minimum-area rectangle from boundary points; discard short side below 3 grid pixels. Expand rectangle by `d = area * 2.3 / perimeter` on each side, then produce four corners.
7. Divide each corner by the **single nominal ratio**, then clip x to `[0, originalWidth]` and y to `[0, originalHeight]`. Rounding means this is not exactly independent `originalWidth/resizedWidth` and `originalHeight/resizedHeight` scaling. There is no centered-letterbox offset to subtract.

Coordinates are continuous original-page pixel positions with top-left origin, x right, y down; right/bottom may equal the page width/height. Quad corners follow the selected rectangle axes, not a guaranteed top-left-first convention. Clipping can collapse edges; normalize/reject nonfinite, empty, or degenerate geometry at the Yomotsu boundary. A downstream axis-aligned bounding box loses rotation and must be an explicit conversion.

The list order is deterministic component discovery order for identical input; it is **not** manga reading order or balloon grouping. Upstream later runs recognition and grouping separately. Confidence is detector component mean probability, not recognized-text confidence.

Upstream default `segThreshold=0.12` affects only its inpainting mask. The Y19 detector result must not transport or render that mask, alter Bubble cleanup, or import grouping to interpret DBNet lines as balloons.

### Exact current JNI contract

```kotlin
external fun createNet(paramPath: String, binPath: String): Long
external fun releaseNet(handle: Long)
private external fun detectDbnetNative(
    handle: Long,
    chw: FloatArray,
    inW: Int,
    inH: Int,
    db: FloatArray,
    mask: FloatArray,
): Int
```

JNI symbols are `Java_li_joye_yakuyomi_engine_NcnnBackend_createNet`, `_releaseNet`, and `_detectDbnetNative`. Native arguments are respectively `(JNIEnv*, jobject, jstring, jstring)`, `(JNIEnv*, jobject, jlong)`, and `(JNIEnv*, jobject, jlong, jfloatArray, jint, jint, jfloatArray, jfloatArray)`.

The handle is a raw `ncnn::Net*` cast to `jlong`; zero means model load failed. The detector caller allocates `chw=3*inW*inH`, `db=2*inW*inH`, `mask=inW*inH`. On nominal success the return value encodes `maskWidth*10000 + maskHeight` (decode by division/remainder). It does **not** return only height despite stale comments, and does not report DB width/height/channels. Zero/negative is failure; `-1` is zero-handle/empty-output and oversized output returns a lossy negative width encoding. Do not expose that encoding as a Yomotsu geometry API.

## Safety gaps that must not be disguised as recoverable exceptions

These are findings from source inspection; reported upstream crash cases were not reproduced on a phone in this inventory.

- **Native process failure is not contained.** `ImageOps.kt` and `docs/MODELS.md` report heap corruption/malloc crashes for square 832–992 inputs. `NcnnBackend.kt` reports a device `__kmp_abort_process`/SIGABRT in concurrent inference and uses a global Kotlin lock. Padding and serialization address known triggers; neither makes arbitrary NCNN faults catchable. `Pipeline`'s `catch (Throwable)` handles managed failures only, not SIGSEGV, SIGABRT, or process death.
- **Input and Java buffers are unchecked in JNI.** No positive-dimension/overflow checks or `GetArrayLength` comparisons precede multiplication and `memcpy`; no null or pending-exception checks guard `GetFloatArrayElements`/`GetStringUTFChars`, and no allocation-empty checks protect input `Mat`. A shorter array, excessive size, or failed JNI/native allocation can cause out-of-bounds access or null dereference before any negative status is returned.
- **Output validation is only partial.** `ex.input` and both `ex.extract` return codes are ignored. The bridge checks only `db.c*db.w*db.h <= 2*area`, `mask.w*mask.h <= area`, and nonzero widths. It does not require expected DB dimensions/channels, mask channels, dimensions, float element size, or unpacked tensor layout. Copying `sizeof(float)*w*h` assumes fp32/unpacked storage. Kotlin then reads a full `area` of DB logits regardless of actual DB shape; a smaller accepted output can produce invented probability values from zero-filled buffers (and larger shape differences can misinterpret channel offsets).
- **Lifecycle is not synchronized with inference.** The bridge's inference call is synchronized, but `Detector.close()` calls `releaseNet` without that lock; the raw pointer can be freed while in use. Handle creation/release are also unguarded. A detector adapter must serialize its entire lifecycle, prohibit post-close inference and double release, and avoid releasing native ownership when only a coroutine is cancelled.
- **Model validation cannot end at filename/existence.** `Detector` loads any `.param` and companion `.bin`; it does not hash them itself. Verify frozen assets before native parsing. This reduces corrupt-model exposure but does not prove the runtime cannot crash.
- **Failure labels are misleading.** Current Kotlin maps any negative detector status, including `-1`, to a dimension-overflow description. Use explicit structured failure kinds rather than preserving that message/encoding.
- **Memory and postprocessing remain costs.** At maximum 1024×1024 input, CHW/db/mask alone contain six million floats (24 MiB), before `prob`, stack, visited map, pixels, native activations, weights, and upstream full-page mask. No arbitrary input-size setting or unbounded concurrent model loading should be introduced.

Bounds checks, pinned models, known-safe sizes, lifecycle serialization, and exception conversion are necessary improvements but are not a process-isolation guarantee. Meeting a strict requirement that a native crash leave current OCR usable requires a containment architecture (for example a separate Android process with explicit IPC/death handling) specified in the design before implementation. Do not silently add a remote service, signal handler, or new scheduler under this detector-only task.

## Existing recognition integration mismatch

Upstream `Pipeline.kt` calls `ocr.recognize(page, lines)` after DBNet. `Ocr.kt` explicitly accepts `Bitmap` plus `List<TextLine>`, rectifies/crops each supplied quad, and recognizes it without re-running the detector. That is why the upstream detector can directly improve its recognized-text recall.

Yomotsu has no equivalent public contract at the inspected branch:

| Yomotsu source | Current behavior |
|---|---|
| `app/src/main/java/eu/kanade/translation/recognizer/OcrEngine.kt` | Only `suspend recognize(OcrImage): OcrPage`; no externally supplied geometry. |
| `app/src/main/java/eu/kanade/translation/ChapterTranslator.kt` | Calls that image-only API, then consumes recognized text and bounds. |
| `app/src/main/java/eu/kanade/translation/recognizer/MlKitOcrEngine.kt` | Calls `recognizer.process(InputImage)`; ML Kit chooses its text regions. |
| `app/src/main/java/eu/kanade/translation/recognizer/PaddleOcrEngine.kt` | Calls `PaddleOCR.recognize(imageBytes)`. |
| `ppocr-sdk/src/main/java/com/paddle/ocr/PaddleOCR.kt` | Bitmap/byte-array recognition routes delegate to `engine.run`. |
| `ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt` | Private `run(Mat)` unconditionally invokes `detectionEngine.detect`, sorts/crops its boxes, then calls its private recognition engine. |

Running ML Kit/PaddleOCR independently on each DBNet crop is possible as a new experiment, but would still execute the existing detector within each crop. It would change crop context, grouping, timings, and potential duplication; missed text can still be rejected by that second detector. It is not an isolated replacement of Y17 detection. Pairing DBNet boxes with full-page recognized blocks cannot recover text never recognized. Importing upstream `Ocr.kt` would replace recognition and violate the Y19 boundary. The public SDK `RecognitionEngine` and `ORTSessionManager` could support an additive recognition adapter without changing the existing engine, but that requires a deliberate design and tests; reuse is not impossible. An approved recognition-only input contract or an explicitly narrowed detector-overlay experiment is needed; no such production change is made here.

## License and attribution requirements

- Engine `LICENSE` and README declare **GPL-3.0**, including its Kotlin implementation. Preserve source attribution to Houri/houri-engine and Yakuyomi, the exact engine pin, applicable notices, and the full GPL text with any adapted source/distribution. Record modifications and provide corresponding source as required if conveying a combined binary. Existing Yomotsu notices must remain intact.
- `models.json` and `docs/MODELS.md` declare the DBNet model files **GPL-3.0**, attributed to manga-image-translator. Keep these notices and distribution/rebuild provenance even when weights are downloaded separately. A hash proves artifact identity, not license compliance.
- NCNN headers carry Tencent copyright and `SPDX-License-Identifier: BSD-3-Clause`; retain those notices and the BSD-3-Clause conditions/disclaimer for binary redistribution. The pinned tree has no standalone NCNN LICENSE file, so collect the exact vendor license before copying/shipping the runtime rather than treating the engine GPL file as sufficient.
- Bundled glslang headers carry several Khronos/contributor notices; for example `build_info.h` has BSD-style redistribution conditions, while `SPIRV/spirv.hpp11` contains a permissive grant. Preserve the full applicable upstream license/notice bundle for the exact component, including static linked components. No complete vendor NOTICE/LICENSE bundle or exact vendor source commits are recorded in this engine checkout; these remain pre-distribution provenance gaps.
- Android/NDK runtime notices and any compiler/runtime components actually linked must be included as applicable. Do not claim an uninspected `libomp` is linked solely from the bridge comment. ONNX Runtime and its notice are not new DBNet requirements because upstream OCR is excluded.

This is an engineering notice inventory, not a legal determination that distribution requirements have already been satisfied. There is no distribution or release in this task.

## Explicit exclusions

Do not import `Ocr.kt`, `alphabet-all-v5.txt`, `ocr_int8.onnx`, `Inpainter.kt`, `mit_aot_fixed512.ncnn.*`, `inpaintAotNative`, `LlmTranslator.kt`, `LlmProviders.kt`, `LlmModels.kt`, `Translator.kt`, `Renderer.kt`, `TextFilter.kt`, `Grouping.kt`, full `Config.kt`, `Pipeline.kt`, `Yakuyomi.create`, `TranslationEngine.kt`, or the complete `ModelSet`/`ModelDownloader` workflow. Their APIs are inspected only to establish the detector boundary.

Houri's reader-side translation service, scheduling, model settings, cache/storage, rendering/typesetting, and cross-page processing are not integration dependencies. Yomotsu's ML Kit/PaddleOCR recognition, translation providers, memory/cache/glossary, Bubble cleanup, Y17 grouping/AutoFit, fonts, auto-scroll, application identity/signing/updater, and release workflow are unchanged.

## Verification record

Verified exact local engine revision/origin, inspected detector/preprocessing/JNI/model/export/build/license paths, measured local source/archive SHA-256 values, and inspected NCNN archive members/symbols and AArch64 ELF metadata. Canonical model hashes are copied from the pinned manifest and were not independently checked against downloaded weights. No Android build, inference, native-fault injection, phone test, or size-delta acceptance claim is made.

Locally measured source anchors:

| Path | SHA-256 |
|---|---|
| `models.json` | `331bab930855e7dada74a591ea59e4b09f7cb99a5f49c8276985fe39a1fd7765` |
| `engine/src/main/kotlin/li/joye/yakuyomi/engine/Detector.kt` | `170b5b39a7989a011ce735bad360542e8c9fce5f7bbc09dd52372433683e7719` |
| `engine/src/main/kotlin/li/joye/yakuyomi/engine/ImageOps.kt` | `ac9e63da308cc4f28a5cf28f4dca95ab5ed1389e68cfa752326b805165a9117f` |
| `engine/src/main/kotlin/li/joye/yakuyomi/engine/Geometry.kt` | `769cadc58a07f312dbc661f128eadb6fa64a5f6cf4f534b041668850d947a0a7` |
| `engine/src/main/kotlin/li/joye/yakuyomi/engine/NcnnBackend.kt` | `dff222ec895a522da4e7493386b4ada478429983c20354629e1d3cc32e717df9` |
| `engine/src/main/cpp/ncnn_jni.cpp` | `d75c6032098ea74416055e8db26ca50a4cd5dd3e825a93b536ae764dc7148982` |
| `engine/src/main/cpp/CMakeLists.txt` | `82feabb2e45be7e16ef68925018c1f2679f58037c8e41eb7af48764b2df58c3b` |
