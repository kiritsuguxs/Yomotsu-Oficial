# Y17 Comic Fit Design

## Goal
Improve the existing Yomotsu speech-balloon translation pipeline so related OCR lines group more reliably and translated text uses the available balloon area without clipping, overflow, or automatic hyphenation.

## Constraints
- Base work on `yomotsu-independent`, only in `agent/y17-comic-fit-test1` until user approval.
- No pull request, merge, or release during testing.
- Preserve applicationId, signing/update compatibility, updater, OCR engines, translators, memory, cache, glossary, manual editing, fonts, cleanup pipeline, and persisted translation compatibility.
- Do not add another OCR/image-processing/model pass.
- Reuse the existing grouping, geometry analysis, and Compose rendering pipeline rather than creating a parallel implementation.

## Reference material
The implementation is informed by the locally inspected Y17 reference pack derived from the two user-provided translation apps. The useful concepts are: vertical proximity, lateral alignment, apparent character size, line-height similarity, safe rectangle validation, and iterative layout measurement with progressive font reduction. The reference binaries themselves are not added to the repository.

## Grouping
Extend `TranslationBlockGrouper` rather than changing either OCR engine. Existing balloon/color/continuation safeguards remain authoritative. Add geometric evidence based on symbol-height similarity, apparent character-width similarity, and left/center alignment. Strong combined evidence can join consecutive fragments of one speech balloon; clear geometry mismatch keeps nearby balloons separate. Strict Paddle grouping remains stricter than normal grouping.

## Safe geometry
All layout regions used for rendering must resolve to finite, positive coordinates inside the page. Existing speech-bubble analysis and rounded cleanup regions remain unchanged. Geometry safety should clamp legacy or edge-touching layout rectangles to page bounds instead of allowing invalid drawing envelopes.

## AutoFit
Keep Compose `SubcomposeLayout` and the existing safe profiles. Start from the source symbol-height-derived ceiling, measure the real wrapped paragraph inside the safe width/height, and select the largest font size that fits. The final layout must be measured without silent height clipping. Horizontal and vertical centering remain. `Hyphens.None` remains mandatory.

The Bubble Translate reference used a one-unit shrink/re-measure loop. Yomotsu already has `TranslationFontSizeSearch`, so Y17 should preserve that native abstraction while ensuring its result is based on the complete measured paragraph and a safe fallback at the minimum readable size.

## Testing
Add regression cases for strongly matching consecutive lines, visually mismatched lines, adjacent distinct balloons, strict Paddle behavior, edge geometry, long translations requiring multiple reductions, and minimum-size fallback. Existing translation/grouping/fit tests must remain green, followed by the Android build/workflow.

## Non-goals
No changes to OCR recognition quality, translation providers/prompts, translation memory, cache/glossary formats, font selection, auto-scroll, theme, updater, or release/version promotion.