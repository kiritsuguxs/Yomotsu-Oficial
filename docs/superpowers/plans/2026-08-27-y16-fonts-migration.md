# Yomotsu Y16 Fonts and Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Comic Font with three officially distributed OFL Regular fonts while preserving every existing Anime Ace and Manga Master BB selection and migrating legacy Comic Font value `2` to Anime Ace.

**Architecture:** `TranslationFont` stops using enum ordinals as persistence values and exposes explicit stable IDs. Value `2` stays reserved and unselectable; the version-76 migration rewrites it to `0`, while runtime decoding normalizes restored legacy or unknown values. Font binaries and license notices come only from the named upstream projects.

**Tech Stack:** Kotlin, Android resources, Mihon migration framework, Compose settings, JUnit Jupiter, SIL OFL font assets, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-27-y16-reader-polish-design.md`

## Global Constraints

- Work only on `agent/y16-reader-polish-test1`, based on `yomotsu-independent` commit `ee221734403d9c6af48197e6c27b9b14ca19e7ff`.
- Keep `applicationId = "app.mihon.tachiyomiat"`, signing, updater, OCR, translators, translation memory/cache/glossary, and translated-text fitting/rendering unchanged.
- Keep Anime Ace value `0` and Manga Master BB value `1`; reserve legacy Comic Font value `2`; assign new values `3..5`.
- Bundle only Regular files; do not infer or synthesize bold/italic styles.
- Do not bundle Anime Ace 3 or Manga Temple, and do not use mirror/re-upload sites.
- Do not merge, open a pull request, tag, publish a release, or promote this branch.

---

### Task 1: Lock stable font preference behavior with failing tests

**Files:**
- Create: `app/src/test/java/eu/kanade/translation/data/TranslationFontTest.kt`

**Contract:**
- `ANIME_ACE.preferenceValue == 0`
- `MANGA_MASTER_BB.preferenceValue == 1`
- `BUBBLE_SANS`, `COMIC_SPICE`, and `BALSAMIQ_SANS` use `3`, `4`, and `5`.
- `TranslationFont.selectableEntries` has exactly those five selectable fonts in the approved display order.
- `TranslationFont.fromPreferenceValue(2)` and unknown integers return Anime Ace.
- `TranslationFont.fromPref()` normalizes legacy/unknown stored values to `0`.

- [ ] **Step 1: Write the red tests**

```kotlin
class TranslationFontTest {
    @Test
    fun `stable values preserve existing choices and reserve comic font`() {
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(0))
        assertEquals(TranslationFont.MANGA_MASTER_BB, TranslationFont.fromPreferenceValue(1))
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(2))
        assertEquals(TranslationFont.BUBBLE_SANS, TranslationFont.fromPreferenceValue(3))
        assertEquals(TranslationFont.COMIC_SPICE, TranslationFont.fromPreferenceValue(4))
        assertEquals(TranslationFont.BALSAMIQ_SANS, TranslationFont.fromPreferenceValue(5))
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(99))
    }

    @Test
    fun `settings order contains no legacy comic entry`() {
        assertEquals(
            listOf(0, 1, 3, 4, 5),
            TranslationFont.selectableEntries.map(TranslationFont::preferenceValue),
        )
        assertFalse(TranslationFont.selectableEntries.any { it.label == "Comic Font" })
    }

    @Test
    fun `stored legacy comic value is normalized`() {
        val pref = InMemoryPreferenceStore().getInt("translation_font", 0)
        pref.set(2)
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPref(pref))
        assertEquals(0, pref.get())
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the explicit API/new enum values do not exist**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.translation.data.TranslationFontTest'
```

Expected: test compilation fails on `preferenceValue`, `selectableEntries`, or new font entries.

---

### Task 2: Acquire and verify only official Regular font assets

**Files:**
- Modify: `.gitattributes` (retain the binary rule for OTF blobs)
- Delete: `app/src/main/res/font/comic_book.otf`
- Create: `app/src/main/res/font/bubble_sans.otf`
- Create: `app/src/main/res/font/comic_spice.ttf`
- Create: `app/src/main/res/font/balsamiq_sans.ttf`
- Create: `app/src/main/assets/licenses/fonts/bubble-sans/OFL.txt`
- Create: `app/src/main/assets/licenses/fonts/bubble-sans/SOURCE.md`
- Create: `app/src/main/assets/licenses/fonts/comic-spice/OFL.txt`
- Create: `app/src/main/assets/licenses/fonts/comic-spice/SOURCE.md`
- Create: `app/src/main/assets/licenses/fonts/balsamiq-sans/OFL.txt`
- Create: `app/src/main/assets/licenses/fonts/balsamiq-sans/SOURCE.md`

**Official sources:**
- Bubble Sans: author page at `https://www.behance.net/gallery/220587045/Bubble-Sans-100-Free-Font`; use the official Google Drive download linked from that page
- ComicSpice: `ComicSpice.ttf` from `https://www.peppercarrot.com/en/fonts/index.html`
- Balsamiq Sans: `https://github.com/balsamiq/balsamiqsans`

- [ ] **Step 1: Download into a temporary directory outside the repository**

Use `mktemp -d`, fetch or clone only the three sources above, and select the upstream Regular file: Bubble Sans as OTF, ComicSpice and Balsamiq Sans as TTF. For Git repositories, record the resolved commit. For each web download, record the final official URL, including the official Google Drive URL linked from the author's Bubble Sans page. Do not convert any file or copy archives, previews, variable fonts, bold, or italic files into the repository.

- [ ] **Step 2: Verify identity, style, glyph coverage, and licenses before copying**

Run `fc-scan --format '%{family}|%{style}|%{fontversion}\n'` against each selected file and reject any file whose style is not Regular. Run `fc-query --format '%{charset}\n'` and confirm `U+00C0..U+00FF` coverage needed by Portuguese. Run `sha256sum` and retain each digest in the corresponding `SOURCE.md` together with upstream URL, commit/download identity, original filename, family/style returned by Fontconfig, and copyright returned by `fc-query --format '%{copyright}\n'`.

- [ ] **Step 3: Copy the verified binaries and complete OFL notices**

Copy the binary bytes without converting or subsetting. Keep the repository `.gitattributes` rule `*.otf binary` so Git preserves the Bubble Sans OTF blob without text conversion. Record the preserved Bubble Sans blob ID from `git hash-object app/src/main/res/font/bubble_sans.otf` together with its `sha256sum` in `SOURCE.md`; the staged blob must match the upstream bytes exactly. Copy the upstream OFL 1.1 text and copyright notice into each license directory. For ComicSpice, use the license/copyright links adjacent to `ComicSpice.ttf` on the Pepper&Carrot page.

- [ ] **Step 4: Remove the old Comic Font binary and verify the asset set**

```bash
test ! -e app/src/main/res/font/comic_book.otf
for file in bubble_sans.otf comic_spice.ttf balsamiq_sans.ttf; do
  test -s "app/src/main/res/font/$file"
done
for font in bubble-sans comic-spice balsamiq-sans; do
  test -s "app/src/main/assets/licenses/fonts/$font/OFL.txt"
  test -s "app/src/main/assets/licenses/fonts/$font/SOURCE.md"
done
```

Expected: every command succeeds; only three new Regular binaries are present.

---

### Task 3: Implement explicit font IDs and the selectable settings map

**Files:**
- Modify: `app/src/main/java/eu/kanade/translation/data/TranslationFont.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt`

- [ ] **Step 1: Replace ordinal persistence with explicit values**

```kotlin
enum class TranslationFont(
    val label: String,
    val res: Int,
    val preferenceValue: Int,
) {
    ANIME_ACE("Anime Ace", R.font.animeace, 0),
    MANGA_MASTER_BB("Manga Master BB", R.font.manga_master_bb, 1),
    BUBBLE_SANS("Bubble Sans", R.font.bubble_sans, 3),
    COMIC_SPICE("ComicSpice", R.font.comic_spice, 4),
    BALSAMIQ_SANS("Balsamiq Sans", R.font.balsamiq_sans, 5),
    ;

    companion object {
        val selectableEntries: List<TranslationFont> = entries

        fun fromPreferenceValue(value: Int): TranslationFont =
            entries.firstOrNull { it.preferenceValue == value } ?: ANIME_ACE

        fun fromPref(pref: Preference<Int>): TranslationFont {
            val font = fromPreferenceValue(pref.get())
            if (font.preferenceValue != pref.get()) pref.set(font.preferenceValue)
            return font
        }
    }
}
```

There is deliberately no enum/resource for legacy value `2`.

- [ ] **Step 2: Build the settings map from explicit values**

Replace the `withIndex()` map with:

```kotlin
entries = TranslationFont.selectableEntries
    .associate { it.preferenceValue to it.label }
    .toImmutableMap()
```

- [ ] **Step 3: Run the focused test and resource compilation**

```bash
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.translation.data.TranslationFontTest'
./gradlew :app:processDebugResources :app:compileDebugKotlin
```

Expected: the mapping tests pass and all five selectable font resource IDs compile.

- [ ] **Step 4: Commit the stable font model and assets**

```bash
git add app/src/main/res/font app/src/main/assets/licenses/fonts \
  .gitattributes \
  app/src/main/java/eu/kanade/translation/data/TranslationFont.kt \
  app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt \
  app/src/test/java/eu/kanade/translation/data/TranslationFontTest.kt
git commit -m "feat: atualizar fontes de tradução da Y16"
```

---

### Task 4: Add the version-76 migration and regression coverage

**Files:**
- Create: `app/src/main/java/mihon/core/migration/migrations/TranslationFontMigration.kt`
- Modify: `app/src/main/java/mihon/core/migration/migrations/Migrations.kt`
- Create: `app/src/test/java/mihon/core/migration/migrations/TranslationFontMigrationTest.kt`

- [ ] **Step 1: Write the migration red tests**

Test `0 -> 0`, `1 -> 1`, `2 -> 0`, each new value remaining unchanged, unknown values normalizing to `0`, and an unset preference remaining at its Anime Ace default. Use `InMemoryPreferenceStore` and a visible-for-test `migrate(preference: Preference<Int>)` method; do not require global Injekt state in the unit test.

- [ ] **Step 2: Run the focused migration test and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests 'mihon.core.migration.migrations.TranslationFontMigrationTest'
```

Expected: compilation fails because `TranslationFontMigration` does not exist.

- [ ] **Step 3: Implement the startup migration**

`TranslationFontMigration.version` is `76f`. `invoke()` obtains `PreferenceStore`, reads `getInt("translation_font", 0)`, calls `migrate`, and returns `false` only when the store is unavailable. `migrate` leaves `0`, `1`, `3`, `4`, and `5` unchanged and writes `0` for every other stored value. Add the migration after `VerticalNavigatorMigration()` in `migrations`.

- [ ] **Step 4: Run migration and font regression tests**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.translation.data.TranslationFontTest' \
  --tests 'mihon.core.migration.migrations.TranslationFontMigrationTest'
```

Expected: all tests pass.

- [ ] **Step 5: Confirm no translation rendering path changed**

```bash
git diff --name-only ee221734403d9c6af48197e6c27b9b14ca19e7ff...HEAD -- \
  app/src/main/java/eu/kanade/translation/presentation \
  app/src/main/java/eu/kanade/translation/translator \
  app/src/main/java/eu/kanade/translation/memory \
  app/src/main/java/eu/kanade/translation/recognizer
```

Expected: no output.

- [ ] **Step 6: Commit the migration**

```bash
git add app/src/main/java/mihon/core/migration/migrations \
  app/src/test/java/mihon/core/migration/migrations/TranslationFontMigrationTest.kt
git commit -m "fix: migrar preferência legada de fonte"
```

---

### Task 5: Font-stage build checkpoint

- [ ] **Step 1: Run formatting and focused translation presentation regressions**

```bash
./gradlew spotlessCheck :app:testDebugUnitTest \
  --tests 'eu.kanade.translation.data.TranslationFontTest' \
  --tests 'mihon.core.migration.migrations.TranslationFontMigrationTest' \
  --tests 'eu.kanade.translation.presentation.*'
```

- [ ] **Step 2: Assemble a debug APK to force Android font packaging**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 3: Inspect the APK and ensure Comic Font is absent**

```bash
unzip -l app/build/outputs/apk/debug/*arm64-v8a*.apk | grep -E 'bubble_sans|comic_spice|balsamiq_sans|licenses/fonts'
if unzip -l app/build/outputs/apk/debug/*arm64-v8a*.apk | grep -q 'comic_book'; then exit 1; fi
```

Expected: the three new resources and license directories are packaged; Comic Font is absent.
