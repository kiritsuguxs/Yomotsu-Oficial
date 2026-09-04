# Yomotsu Y16 Cover Accent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive one restrained, contrast-safe accent from the current manga cover and apply it only to manga-screen and reader-bar emphasis, with exact fallback to the current theme.

**Architecture:** A pure pixel extractor selects a seed from a 64-pixel software thumbnail. A bounded synchronized LRU stores only ARGB integers by the same manga/cover identity as Coil. A cancellable composable loader uses the application `ImageLoader` off the main thread. `CoverAccentTheme` copies generated Material Kolor accent roles over the current scheme while preserving every surface/background/error role; its scope wraps the manga screen and reader app bars only.

**Tech Stack:** Kotlin, Coil 3, Android Bitmap/Drawable, coroutines, Jetpack Compose Material 3, Material Kolor, JUnit Jupiter.

**Spec:** `docs/superpowers/specs/2026-08-27-y16-reader-polish-design.md`

## Global Constraints

- Execute after fonts and auto-scroll on `agent/y16-reader-polish-test1`.
- Do not save a theme preference or mutate the app-level `TachiyomiTheme`.
- Do not recolor page canvas, images, translation overlay, reader background, brightness/color filters, surfaces, or blurred cover backdrop.
- Missing, corrupt, transparent, grayscale, very dark, or very light covers must use the unmodified current `ColorScheme`.
- Decode a small software image through the existing Coil configuration/cache; never hold Activity references or cache Bitmap/Drawable objects.
- Cancel analysis when manga changes or the composable leaves composition.

---

### Task 1: Implement deterministic color extraction test-first

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/theme/CoverAccentExtractor.kt`
- Create: `app/src/test/java/eu/kanade/presentation/theme/CoverAccentExtractorTest.kt`

**Interface:**

```kotlin
object CoverAccentExtractor {
    fun selectSeedColor(pixels: IntArray): Int?
}
```

- [ ] **Step 1: Write failing pure-JVM tests**

Cover:

- A dominant opaque blue/red/green sample returns that quantized family.
- Transparent pixels are ignored.
- Near-white (`luminance > 0.94`), near-black (`< 0.04`), and saturation below `0.12` are rejected.
- A vivid minority is not allowed to beat a much larger usable dominant bin solely by saturation.
- Equal-score bins resolve deterministically by bin key.
- Empty/all-rejected input returns null.
- Alpha in the selected result is always `0xff`.

- [ ] **Step 2: Run focused tests and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.presentation.theme.CoverAccentExtractorTest'
```

- [ ] **Step 3: Implement quantized histogram selection without Android color APIs**

Use bit operations for ARGB, 5 bits per RGB channel, and a per-bin count plus channel sums. Ignore alpha below `0xc0`. Convert averaged RGB to HSL with pure Kotlin helpers. Candidate score is `count * (0.75 + saturation * 0.25)` after rejection thresholds; highest score wins, with the integer bin key as deterministic tiebreaker. Return the averaged opaque RGB of the winning bin. Do not allocate per pixel.

- [ ] **Step 4: Run tests until green and commit**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.presentation.theme.CoverAccentExtractorTest'
git add app/src/main/java/eu/kanade/presentation/theme/CoverAccentExtractor.kt \
  app/src/test/java/eu/kanade/presentation/theme/CoverAccentExtractorTest.kt
git commit -m "feat: extrair cor de destaque da capa"
```

---

### Task 2: Add bounded cache and cancellable Coil loading

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/theme/CoverAccentCache.kt`
- Create: `app/src/main/java/eu/kanade/presentation/theme/MangaCoverAccent.kt`
- Create: `app/src/test/java/eu/kanade/presentation/theme/CoverAccentCacheTest.kt`

**Interfaces:**

```kotlin
internal data class CachedCoverAccent(val color: Int?)

internal class CoverAccentCache(private val maxEntries: Int = 24) {
    fun get(key: String): CachedCoverAccent?
    fun put(key: String, color: Int?)
}

@Composable
fun rememberMangaCoverAccent(manga: Manga?): Color?
```

- [ ] **Step 1: Write red LRU tests**

Test hit, null-result caching, oldest-entry eviction at 24, access-order refresh, thread-safe repeated get/put, and distinct keys when `coverLastModified` changes.

- [ ] **Step 2: Run cache tests and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.presentation.theme.CoverAccentCacheTest'
```

- [ ] **Step 3: Implement the bounded cache**

Use a synchronized access-order `LinkedHashMap<String, CachedCoverAccent>`. A missing key returns null; a known extraction failure returns `CachedCoverAccent(color = null)`, so repeated failures do not decode again. Remove the eldest entry when size exceeds `maxEntries`. Validate `maxEntries > 0`. Store only the small wrapper and `Int?`, never image objects or contexts.

- [ ] **Step 4: Implement cover identity matching `MangaKeyer`**

The key is `"${manga.id};${manga.coverLastModified}"` for a custom cover and `"${manga.thumbnailUrl};${manga.coverLastModified}"` otherwise, using the existing `hasCustomCover()` extension. A null/blank remote URL with no custom cover immediately falls back.

- [ ] **Step 5: Implement cancellable off-main loading**

Use `produceState<Color?>(initialValue = cached?.color?.let(::Color), key1 = coverKey)` and `withContext(Dispatchers.IO)`. If `cache.get(coverKey)` returns a wrapper, publish its color or cached fallback without decoding. Otherwise build an `ImageRequest` with `.data(manga)`, `.size(64, 64)`, and software bitmap configuration; execute through `context.applicationContext.imageLoader`. Convert the returned image to a drawable/bitmap with existing Coil helpers, scale only if necessary, copy pixels into an `IntArray`, immediately release local image references, and call `CoverAccentExtractor`. Catch decode/analysis exceptions (but rethrow `CancellationException`) and cache null. A changed key cancels the prior producer and cannot publish its result for the new manga.

- [ ] **Step 6: Run cache/extractor tests and compile Coil integration**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.presentation.theme.CoverAccentExtractorTest' \
  --tests 'eu.kanade.presentation.theme.CoverAccentCacheTest'
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 7: Commit loading and cache**

```bash
git add app/src/main/java/eu/kanade/presentation/theme/CoverAccentCache.kt \
  app/src/main/java/eu/kanade/presentation/theme/MangaCoverAccent.kt \
  app/src/test/java/eu/kanade/presentation/theme/CoverAccentCacheTest.kt
git commit -m "feat: carregar destaque da capa com cache"
```

---

### Task 3: Generate safe local accent roles and prove fallback/contrast

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt`
- Create: `app/src/main/java/eu/kanade/presentation/theme/CoverAccentTheme.kt`
- Create: `app/src/test/java/eu/kanade/presentation/theme/CoverAccentThemeTest.kt`

**Interfaces:**

```kotlin
internal fun coverAccentColorScheme(base: ColorScheme, seed: Color?, dark: Boolean): ColorScheme

@Composable
fun MangaCoverAccentTheme(manga: Manga?, content: @Composable () -> Unit)
```

- [ ] **Step 1: Write red scheme tests**

For both `lightColorScheme()` and `darkColorScheme()`:

- Null seed returns the exact same scheme instance/value.
- Seed replaces `primary`, `onPrimary`, `primaryContainer`, and `onPrimaryContainer` from Material Kolor.
- `background`, `surface`, all `surfaceContainer*`, `inverseSurface`, `error`, `outline`, and scrim roles remain equal to base.
- WCAG contrast for `primary/onPrimary` and `primaryContainer/onPrimaryContainer` is at least 4.5:1.

- [ ] **Step 2: Expose existing seed generation within the theme package**

Change only the visibility needed for `MonetCompatColorScheme.generateColorSchemeFromSeed`; do not alter its `SPEC_2025`, `PaletteStyle.Expressive`, light/dark, or AMOLED behavior.

- [ ] **Step 3: Implement local scheme copying**

Call existing Material Kolor generation and copy only the four primary roles listed above onto `base`. `MangaCoverAccentTheme` obtains the remembered seed, derives dark mode from the current base scheme's background luminance, remembers the derived scheme by base/seed/dark, and wraps content with `MaterialExpressiveTheme(colorScheme = derived, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes)`. This prevents the local color scope from resetting typography or shapes.

- [ ] **Step 4: Run scheme tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.presentation.theme.CoverAccentThemeTest'
git add app/src/main/java/eu/kanade/presentation/theme \
  app/src/test/java/eu/kanade/presentation/theme/CoverAccentThemeTest.kt
git commit -m "feat: aplicar esquema local de destaque"
```

---

### Task 4: Scope the accent to manga content and reader bars

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- Create: `app/src/test/java/eu/kanade/presentation/theme/CoverAccentScopeContractTest.kt`

- [ ] **Step 1: Write a source-scope red test**

Assert that `MangaScreen` wraps its small/large implementation selection with `MangaCoverAccentTheme(state.manga)` and that `ReaderActivity.AppBars` wraps only the `ReaderAppBars` call. Assert `ReaderContentOverlay`, viewer container creation, and page holders are not inside or passed through the cover theme.

- [ ] **Step 2: Wrap the manga screen locally**

At the root of the presentation `MangaScreen`, call `MangaCoverAccentTheme(state.manga)` around the existing small-vs-large branch. Do not change either implementation's layout or surface colors. The existing FAB and selected controls naturally consume local `primary` roles.

- [ ] **Step 3: Wrap reader controls only**

Inside `ReaderActivity.AppBars`, wrap the existing `ReaderAppBars(...)` call with `MangaCoverAccentTheme(state.manga)`. Keep `ContentOverlay`, Android viewer views, translation views, and reader background outside. When `state.manga` changes or becomes null, the producer key changes and normal fallback applies.

- [ ] **Step 4: Run scope, extraction, cache, and contrast tests**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.presentation.theme.*'
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 5: Commit scoped UI integration**

```bash
git add app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt \
  app/src/test/java/eu/kanade/presentation/theme/CoverAccentScopeContractTest.kt
git commit -m "feat: destacar obra e leitor com cor da capa"
```

---

### Task 5: Cover-accent checkpoint

- [ ] **Step 1: Run formatting, all theme tests, and assemble debug**

```bash
./gradlew spotlessCheck :app:testDebugUnitTest --tests 'eu.kanade.presentation.theme.*'
./gradlew :app:assembleDebug
```

- [ ] **Step 2: Review touched UI boundaries**

```bash
git diff --stat ee221734403d9c6af48197e6c27b9b14ca19e7ff...HEAD
rg -n "MangaCoverAccentTheme" app/src/main/java
```

Expected: exactly the manga-screen root and reader app-bars call site consume the local theme; the extractor/loader/theme helpers contain the remaining references.

- [ ] **Step 3: Perform visual smoke checks when an emulator/device is available**

Open colorful, grayscale, missing-cover, light, and dark-cover works under light and dark app themes. Confirm readable primary actions, unchanged surfaces/backdrop/page canvas/translation overlay, no prior-manga color leakage, and ordinary theme fallback. Record device/API and outcome for delivery.
