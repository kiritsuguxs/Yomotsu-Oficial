# Y19 DBNet + ML Kit Hybrid Design

## Goal
Replace only the experimental DBNet recognition route with a hybrid that preserves Y17 translation/cleanup/render behavior while improving DBNet quality and speed: DBNet detection + Yakuyomi-style line grouping + one full-page ML Kit recognition pass + DBNet/ML Kit geometric association + DBNet segmentation mask constrained to translated regions.

## Constraints
- Work only on `agent/y19-dbnet-mlkit-test1`, based on Y19 DBNet baseline `b9d20f16f140710a09076ea7d794b062224f8041`.
- Do not modify `yomotsu-independent`.
- No PR, merge, release, or official promotion.
- Preserve applicationId, signing/update compatibility, translators, translation memory, cache, glossary, Y17 Bubble fitting/rendering, and normal ML Kit/Paddle OCR choices.
- DBNet remains experimental, ARM64/English gated, disabled by default, with its ~153 MB weights outside the APK.
- Paddle remains available as an ordinary OCR engine; it is not the recognizer for this experimental DBNet hybrid.
- Any experimental failure falls back for the whole page to the selected existing OCR; never mix partial experimental and fallback blocks.
- No AOT-GAN and no Yakuyomi renderer in this experiment.

## Root cause
The first Y19 route maps DBNet connected components directly to Paddle crops/OCR blocks and explicitly discards the DBNet segmentation mask before cleanup. Houri/Yakuyomi instead treats DBNet output as text lines, groups lines into coherent regions before translation, and retains the segmentation mask for text removal. The observed phone regressions (English remnants, PT/EN overlap, fragmented bubbles) are consistent with this missing grouping/mask information. The current route is also expensive because it reopens/decodes the page, converts to OpenCV BGR, crops every DBNet region, and runs Paddle CTC batches.

## Architecture
1. Keep the existing isolated DBNet worker and persistent native session.
2. Extend detection transport to carry the DBNet segmentation mask in a bounded representation alongside text-line quads.
3. Adapt the relevant Houri/Yakuyomi grouping algorithm: permissive line linking followed by MST outlier splitting, preserving reading order and preventing adjacent bubbles from becoming one region.
4. Run ML Kit once on the full original page using the existing ML Kit engine/session rather than once per DBNet crop.
5. Geometrically associate ML Kit blocks/lines with grouped DBNet regions. A successful experimental page must produce one coherent set of blocks; ambiguous/invalid experimental output causes whole-page fallback.
6. Feed coherent OCR blocks into the existing Y17 translation path.
7. Restrict the DBNet mask to regions that are actually translated, apply only a small tested dilation, and expose it to Y17 cleanup without replacing Y17 rendering/fitting.
8. Add stage timing for DBNet preparation/inference/postprocess, grouping, ML Kit, association, and mask preparation so phone tests identify the true bottleneck.

## Association rules
- Coordinate systems are normalized to original page pixels before matching.
- Prefer overlap/intersection evidence; center containment is a secondary signal.
- Multiple DBNet lines may map to one ML Kit text block and become one coherent translation region.
- Multiple ML Kit lines may map to one grouped DBNet region when their geometry agrees.
- Adjacent grouped DBNet regions must remain separate unless grouping explicitly joined them.
- Do not emit duplicate text blocks.
- If association yields no usable text while either detector saw meaningful text, fail the experimental page and invoke whole-page fallback.

## Mask rules
- Mask transport must be bounded and validated before allocation/use.
- Mask is mapped back to original page coordinates.
- Cleanup mask is the DBNet text mask intersected with the union of successfully translated regions, with a small dilation to cover glyph antialiasing.
- Mask must never erase outside translated regions.
- If mask metadata is invalid, do not attempt partial cleanup; use safe whole-page fallback/legacy behavior as appropriate before translation side effects.

## Performance policy
The first target is correctness plus measurement, not a promised one-minute chapter. Eliminate Paddle crop recognition first. Do not introduce multiple simultaneous 153 MB DBNet sessions. Keep DBNet native inference serial until measured evidence proves it is the bottleneck and concurrency is safe. Lower detector resolution or DBNet rescue-mode are later experiments, not part of this change.

## Verification
Use TDD. Add focused unit tests for grouping, MST separation, DBNet/ML Kit association, coordinate mapping, deduplication, mask clipping/dilation bounds, invalid mask handling, whole-page fallback, and timing bookkeeping. Preserve existing DBNet native/sanitizer/process-death tests and full Yomotsu unit suite. Build the signed ARM64 APK through the existing workflow and compare APK size with the Y19 baseline. Phone acceptance uses the previously observed problematic chapter/pages plus a timing run; no promotion occurs without user approval.
