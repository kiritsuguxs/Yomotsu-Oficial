# Yomotsu Y16 Reader Polish Design

## Context

Yomotsu `0.20.4-Y15` stores the translation font as the ordinal position of
`TranslationFont.entries`. The available fonts are Anime Ace, Manga Master BB, and
Comic Font. The reader has separate pager and webtoon viewers, but it has no
session-level automatic scrolling. Manga covers are already loaded through Coil and
shown as a blurred backdrop on the manga screen, while the app theme remains global.

Y16 adds conservative polish in these three areas without changing OCR, translators,
translation memory, cache, glossary, bubble grouping, cleanup, or translated-text
rendering. All work remains on `agent/y16-reader-polish-test1` until the test APK is
approved. `yomotsu-independent` is not modified or promoted.

## Goals

- Keep Anime Ace as the default and Manga Master BB available.
- Remove Comic Font from the selectable list and safely migrate its users to Anime
  Ace.
- Add the Regular variants of Bubble Sans, ComicSpice, and Balsamiq Sans.
- Add smooth, manually controlled auto-scroll to the webtoon and continuous vertical
  viewers without changing the selected reading mode.
- Derive a safe accent from the manga cover for restrained use on the manga screen
  and reader controls.
- Preserve light/dark contrast and fall back to the existing theme when cover analysis
  is unavailable.
- Prepare a signed arm64 Y16 test APK using the existing package, updater, and signing
  identity.

## Non-goals

- Automatic webtoon detection or automatic reading-mode changes.
- Auto-scroll in paged horizontal or paged vertical modes.
- Bold/italic font selection or automatic font-style switching.
- A global theme generated from the cover or permanent theme preference changes.
- Changes to translation providers, OCR engines, translation prompts, memory, cache,
  glossary, cleanup, grouping, layout fitting, manual editing, downloads, or queues.
- Refactoring unrelated reader, manga, theme, or translation code.
- A pull request, merge into `yomotsu-independent`, tag, or official release.

## Selected Fonts and Licensing

The selectable Y16 font list is:

| Preference value | Font | Source/license | Selectable |
| ---: | --- | --- | --- |
| 0 | Anime Ace | Existing bundled font | Yes; default |
| 1 | Manga Master BB | Existing bundled font | Yes |
| 2 | Legacy Comic Font sentinel | No font resource | No; migrates to 0 |
| 3 | Bubble Sans Regular | Official project, SIL OFL 1.1 | Yes |
| 4 | ComicSpice Regular | Pepper&Carrot font collection, SIL OFL 1.1 | Yes |
| 5 | Balsamiq Sans Regular | Official Balsamiq repository, SIL OFL 1.1 | Yes |

ComicSpice replaces the requested Manga Temple role because it is a condensed comic
dialogue face with Latin Extended coverage, making it suitable for longer Portuguese
translations inside small balloons. Balsamiq Sans replaces the requested Anime Ace 3
role because it is a highly legible handwritten face with broad Latin and punctuation
coverage. Both may be embedded and redistributed with software under the OFL.

Only upstream Regular font binaries are bundled. Each font's copyright and OFL text
is retained under `app/src/main/assets/licenses/fonts/<font>/`, so the licence remains
inside the distributed APK as well as the source repository. No file is taken from an
unofficial font mirror.

## Stable Font Preferences and Migration

`TranslationFont` gains explicit integer preference values instead of deriving saved
values from enum positions. Settings build their map from selectable entries and use
those explicit values.

A version 76 app migration checks the existing `translation_font` preference:

- `0` remains Anime Ace.
- `1` remains Manga Master BB.
- `2` is rewritten to `0`, so former Comic Font users move to Anime Ace.
- Missing or unknown values resolve to Anime Ace and are normalized to `0`.

The runtime decoder also treats legacy value `2` and unknown values as Anime Ace. This
second layer protects users who restore an old backup after the one-time app migration.
New fonts use values `3` through `5`, so they cannot silently reinterpret an old
selection.

## Auto-scroll Scope and User Experience

Auto-scroll is available only when the active viewer is `WebtoonViewer`, which covers
the Webtoon and Continuous Vertical reading modes. The feature never creates or
selects a viewer and therefore cannot change the manga's saved reading mode.

The reader bottom bar shows an auto-scroll play/pause action only for a compatible
viewer. Auto-scroll starts disabled whenever `ReaderActivity` opens. Its enabled state
is session-only, preventing a chapter from unexpectedly moving when the reader is
reopened. Speed is a persisted reader preference and is adjustable from 20 to 180
dp/second, in 10 dp/second steps, with a default of 60 dp/second. A labelled slider in
the Webtoon section of the in-reader settings dialog controls this value.

Starting auto-scroll hides no existing controls. Tapping the play/pause action toggles
the session state immediately. A touch or manual drag on the webtoon content pauses
auto-scroll before normal gesture handling continues, so zoom, taps, manual scrolling,
long-press translation editing, and navigation remain authoritative. The user can
resume from the bottom bar.

At the end of available content, auto-scroll stops and the existing end-of-chapter
menu behavior remains in control. Viewer destruction, a reading-mode change, leaving
the activity, or an error cancels the scrolling job.

## Auto-scroll Runtime Design

`WebtoonViewer` owns a small session controller because it already owns the
`RecyclerView`, layout manager, lifecycle scope, and scroll callbacks. A frame-driven
loop calculates movement from elapsed time and the configured pixels-per-second
speed. Fractional pixels are accumulated and only whole-pixel deltas are sent to
`RecyclerView.scrollBy`, avoiding timer drift and repeated long `smoothScrollBy`
animations.

The loop exists only while auto-scroll is active, clamps unusually large frame gaps,
and performs no background polling while paused. This keeps work proportional to
visible scrolling and prevents queued animations from fighting manual input. Speed
changes are observed without recreating the viewer.

The calculation and state transitions are separated from Android view calls so they
can be unit tested deterministically.

## Cover Accent Extraction

A cover-accent component loads the existing `Manga` cover through the app's configured
Coil `ImageLoader`, benefiting from the same memory/disk caches and custom-cover
handling. Analysis uses a small software bitmap, not the full cover, and runs off the
main thread.

The extractor samples opaque pixels into a quantized histogram, rejects near-white,
near-black, and very low-information candidates, and favors a sufficiently saturated
representative color. Failure, cancellation, a missing cover, or an unsuitable image
returns no accent. Results are held in a bounded in-memory cache keyed with the same
manga ID and `coverLastModified` identity used by `MangaCoverKeyer`, so recomposition
and reader entry do not repeatedly decode the image and a changed custom cover
invalidates the old accent.

The selected seed is passed through the existing Material Kolor dependency to derive
contrast-safe light or dark primary roles. Only accent roles are copied onto the
current theme; background, surface, error, typography, shapes, and every other global
theme choice remain unchanged.

## Accent Scope

On the manga screen, the local accent is limited to safe interactive emphasis such as
the continue-reading floating action button, selected/active controls, and primary
action highlights. Existing surfaces and the blurred cover backdrop do not change.

In the reader, the accent is scoped to `ReaderAppBars` and their interactive controls,
including the active auto-scroll action and navigator emphasis. The page canvas,
images, translation overlay, reader background, brightness/color filters, and global
application theme are unaffected.

When no accent is available, these components receive the unmodified current color
scheme. Switching manga cannot leave the previous manga's accent applied.

## Error Handling and Stability

- Font decoding always has Anime Ace as a valid fallback.
- A failed font migration does not block startup; runtime decoding still normalizes
  the value when the translation overlay is used.
- Missing or corrupt cover data produces the ordinary theme, never a blank screen.
- Accent extraction is cancellable and does not retain activities, bitmaps, or manga
  screens.
- Auto-scroll cannot run after its viewer is destroyed.
- User touch, mode replacement, and end-of-content transitions stop or pause scrolling
  before existing reader behavior continues.
- No exception in cover analysis is allowed to escape into manga or reader UI state.

## Compatibility and Versioning

- Base branch and commit: `yomotsu-independent` at
  `ee221734403d9c6af48197e6c27b9b14ca19e7ff`.
- Test branch: `agent/y16-reader-polish-test1`.
- `applicationId`: `app.mihon.tachiyomiat` (unchanged).
- `versionCode`: `76`.
- `versionName`: `0.20.4-Y16-test1`.
- Signing, updater configuration, OCR assets, and translation code guards remain
  intact.

The manual Yomotsu build workflow already runs for `agent/y*` pushes. Its hard-coded
version and APK identity checks are updated from Y15/75 to Y16-test1/76. Existing OCR
asset hashes, signer digest, updater checks, and translation regression commands stay
unchanged.

## Tests

Automated coverage includes:

- Stable font values and selectable ordering.
- Legacy Comic Font value `2` migrating and decoding to Anime Ace.
- Anime Ace and Manga Master BB retaining values `0` and `1`.
- Presence and loadability of all three new Regular font resources and OFL notices.
- Deterministic auto-scroll distance calculation, fractional accumulation, large-frame
  clamping, pause/resume, end-of-content stop, and destruction cancellation.
- Manual interaction pausing auto-scroll without consuming the original gesture.
- Cover dominant-color selection, rejection of unusable covers, bounded caching,
  fallback behavior, and minimum foreground/background contrast in light and dark
  schemes.
- Existing translation model, memory, cache, glossary, translator, recognizer, and
  presentation regression suites.
- `spotlessCheck`, focused unit tests, full `:app:testDebugUnitTest`, and release APK
  assembly.
- Workflow verification of package ID, version, OCR assets, updater identity, and
  canonical APK signature.

## Acceptance Criteria

The Y16 test is ready for phone testing when:

1. Updating from Y15 preserves Anime Ace and Manga Master BB selections.
2. A previous Comic Font selection opens with Anime Ace selected.
3. Bubble Sans, ComicSpice, and Balsamiq Sans Regular render translated text in
   pager and webtoon viewers.
4. Auto-scroll appears only for Webtoon and Continuous Vertical, starts disabled, can
   be paused immediately, respects speed changes, and yields to touch/zoom/manual
   navigation.
5. Manga and reader accents follow the current cover without harming contrast, and
   missing covers use the normal theme.
6. Translation behavior and all preserved Y15 subsystems pass their regression tests.
7. GitHub Actions produces an installable, correctly signed arm64
   `Yomotsu-0.20.4-Y16-test1` artifact from the temporary branch.
8. No pull request, merge, tag, official release, or promotion occurs before user
   approval.

## Font Sources

- Bubble Sans project: <https://github.com/abayemes/bubblesans>
- Pepper&Carrot ComicSpice: <https://www.peppercarrot.com/en/fonts/index.html>
- Balsamiq Sans: <https://github.com/balsamiq/balsamiqsans>
