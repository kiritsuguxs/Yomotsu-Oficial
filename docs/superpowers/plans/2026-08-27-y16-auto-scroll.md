# Yomotsu Y16 Auto-scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add smooth, session-only auto-scroll to Webtoon and Continuous Vertical readers, with an immediate play/pause action and a persisted speed control that yields to every manual gesture.

**Architecture:** An Android-independent `AutoScrollController` owns session state and an active-only coroutine. It receives an injected frame clock, speed, scrollability, and scroll callback. `WebtoonViewer` supplies Choreographer frames and `RecyclerView.scrollBy`; `WebtoonFrame` pauses on `ACTION_DOWN` before forwarding the untouched gesture. Compose only exposes controls when the active viewer is `WebtoonViewer`.

**Tech Stack:** Kotlin coroutines/StateFlow, Android Choreographer, RecyclerView, Jetpack Compose Material 3, moko-resources, JUnit Jupiter/coroutines-test.

**Spec:** `docs/superpowers/specs/2026-08-27-y16-reader-polish-design.md`

## Global Constraints

- Execute after the font/migration plan on `agent/y16-reader-polish-test1`.
- Do not change or auto-detect a reading mode; auto-scroll is compatible only with the already-created `WebtoonViewer`.
- Start disabled on every new viewer/activity; persist speed only.
- A content `ACTION_DOWN`, end of content, viewer destruction, or viewer replacement must stop/pause the loop without consuming input.
- Do not alter zoom, tap navigation, long-press editing, translation overlays, page selection, chapter transitions, or image rendering.
- Do not poll while disabled and do not queue `smoothScrollBy` animations.

---

### Task 1: Build the deterministic auto-scroll controller test-first

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/AutoScrollController.kt`
- Create: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/AutoScrollControllerTest.kt`

**Interfaces:**

```kotlin
fun interface AutoScrollFrameClock {
    suspend fun awaitFrameNanos(): Long
}

class AutoScrollController(
    private val scope: CoroutineScope,
    private val frameClock: AutoScrollFrameClock,
    private val speedPixelsPerSecond: () -> Float,
    private val canScrollForward: () -> Boolean,
    private val scrollBy: (Int) -> Unit,
    private val onEndReached: () -> Unit,
) {
    val enabled: StateFlow<Boolean>
    fun toggle()
    fun start()
    fun pause()
    fun destroy()
}
```

- [ ] **Step 1: Write red tests with a channel-backed fake frame clock**

Cover these cases:

- Initial state is disabled and no frame is requested.
- `start()` requests frames; `pause()` cancels the loop and clears fractional distance.
- 60 px/s over ten 10-ms intervals produces exactly 6 pixels despite fractions.
- A frame gap larger than 100 ms is clamped to 100 ms.
- A speed supplier change affects the next frame without restarting.
- `canScrollForward == false` pauses, calls `onEndReached` once, and performs no scroll.
- `destroy()` disables/cancels permanently; later `start()` is ignored.

- [ ] **Step 2: Run the focused test and verify red**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.AutoScrollControllerTest'
```

Expected: compilation fails because the controller does not exist.

- [ ] **Step 3: Implement fractional accumulation and lifecycle state**

Use elapsed nanoseconds, a `MAX_FRAME_DELTA_NANOS` of `100_000_000L`, and a `Double` fractional remainder. The first frame establishes a baseline and emits no distance. For each later frame:

```kotlin
remainder += clampedDeltaNanos / 1_000_000_000.0 * speedPixelsPerSecond()
val pixels = remainder.toInt()
remainder -= pixels
if (pixels > 0) scrollBy(pixels)
```

`start()` creates exactly one job. `pause()` cancels it, publishes `false`, and resets the frame/remainder state. `destroy()` marks the controller terminal before calling `pause()`. No exception escapes the job; cancellation remains normal, and unexpected exceptions pause the controller.

- [ ] **Step 4: Run the test until green**

Run the Step 2 command. Expected: all controller cases pass.

- [ ] **Step 5: Commit the controller**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/AutoScrollController.kt \
  app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/AutoScrollControllerTest.kt
git commit -m "feat: criar controle eficiente de auto-scroll"
```

---

### Task 2: Integrate Choreographer, WebtoonViewer, and manual pause

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/ChoreographerAutoScrollFrameClock.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonFrame.kt`
- Create: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonFrameInteractionContractTest.kt`

- [ ] **Step 1: Add a source contract test for non-consuming manual pause**

The test reads `WebtoonFrame.kt` and asserts that `MotionEvent.ACTION_DOWN` invokes `onUserInteraction` before the existing scale/fling detector calls, and that `dispatchTouchEvent` still returns `super.dispatchTouchEvent(ev)`. This regression guard is intentionally source-level because the local JVM test suite has no Android input dispatcher.

- [ ] **Step 2: Verify the contract test fails**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonFrameInteractionContractTest'
```

- [ ] **Step 3: Implement a cancellation-safe Choreographer clock**

`awaitFrameNanos()` uses `suspendCancellableCoroutine`, posts one `Choreographer.FrameCallback`, resumes with its timestamp, and removes the callback on cancellation. It retains no Activity or View.

- [ ] **Step 4: Wire the controller into WebtoonViewer**

Add:

- A single injected `ReaderPreferences` instance and density conversion from persisted dp/s to px/s.
- `val autoScrollEnabled: StateFlow<Boolean> = controller.enabled`.
- `fun toggleAutoScroll()` and `fun pauseAutoScroll()` delegates.
- `frame.onUserInteraction = ::pauseAutoScroll`.
- `canScrollForward = { recycler.canScrollVertically(1) }`.
- `scrollBy = { pixels -> recycler.scrollBy(0, pixels) }`.
- `onEndReached = activity::showMenu`.
- `controller.destroy()` before `scope.cancel()` in `destroy()`.

Do not touch `scrollUp`, `scrollDown`, page selection, tap/long-tap listeners, zoom configuration, or adapter behavior.

- [ ] **Step 5: Pause before forwarding every content ACTION_DOWN**

Add `var onUserInteraction: (() -> Unit)? = null` to `WebtoonFrame`. At the first line of `dispatchTouchEvent`, call it only when `ev.actionMasked == MotionEvent.ACTION_DOWN`; then execute the existing detector, coordinate-clamp, and `super` logic unchanged.

- [ ] **Step 6: Run controller and interaction tests, then compile**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.AutoScrollControllerTest' \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonFrameInteractionContractTest'
./gradlew :app:compileDebugKotlin
```

Expected: tests pass and the viewer compiles.

- [ ] **Step 7: Commit viewer integration**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon \
  app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon
git commit -m "feat: integrar auto-scroll ao leitor vertical"
```

---

### Task 3: Add speed preference and in-reader controls

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`
- Modify: `i18n/src/commonMain/moko-resources/pt-rBR/strings.xml`
- Create: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderAutoScrollPreferencesTest.kt`

- [ ] **Step 1: Write preference-default/range tests**

Test the key `pref_webtoon_auto_scroll_speed`, default `60`, and public constants `AUTO_SCROLL_SPEED_MIN = 20`, `AUTO_SCROLL_SPEED_MAX = 180`, `AUTO_SCROLL_SPEED_STEP = 10`.

- [ ] **Step 2: Add the persisted speed preference**

```kotlin
val autoScrollSpeed: Preference<Int> = preferenceStore.getInt(
    "pref_webtoon_auto_scroll_speed",
    AUTO_SCROLL_SPEED_DEFAULT,
)
```

Define the four speed constants in `ReaderPreferences.Companion` and clamp the value when consumed by the viewer. Do not persist the enabled state.

- [ ] **Step 3: Add the Webtoon settings slider**

In `WebtoonViewerSettings`, collect `autoScrollSpeed` and add `SliderItem` with `valueRange = 20..180 step 10`, `steps = 15`, and a localized value such as `60 dp/s`. It remains inside the existing `viewer is WebtoonViewer` branch, so paged modes never show it.

- [ ] **Step 4: Add localized action and speed strings**

Add base English and Brazilian Portuguese strings for:

- `reader_auto_scroll_start`: “Start auto-scroll” / “Iniciar rolagem automática”
- `reader_auto_scroll_pause`: “Pause auto-scroll” / “Pausar rolagem automática”
- `pref_auto_scroll_speed`: “Auto-scroll speed” / “Velocidade da rolagem automática”
- `auto_scroll_speed_value`: `%1$d dp/s` in both locales

- [ ] **Step 5: Expose play/pause only for the active WebtoonViewer**

In `ReaderActivity.AppBars`, cast `state.viewer as? WebtoonViewer`, collect its `autoScrollEnabled` when non-null, and pass nullable `autoScrollEnabled` plus `onToggleAutoScroll`. `ReaderAppBars` forwards both to `ReaderBottomBar`. `ReaderBottomBar` inserts a filled `PlayArrow` or `Pause` `IconButton` only when the nullable state is present, using the localized content description. Existing four buttons and callbacks remain unchanged.

- [ ] **Step 6: Run resources, focused tests, and Compose compilation**

```bash
./gradlew :i18n:generateMRcommonMain :app:testDebugUnitTest \
  --tests 'eu.kanade.tachiyomi.ui.reader.setting.ReaderAutoScrollPreferencesTest' \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.*'
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 7: Commit controls and localization**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt \
  app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt \
  app/src/main/java/eu/kanade/presentation/reader/appbars \
  app/src/test/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderAutoScrollPreferencesTest.kt \
  i18n/src/commonMain/moko-resources
git commit -m "feat: adicionar controles de auto-scroll"
```

---

### Task 4: Auto-scroll checkpoint

- [ ] **Step 1: Run formatting and all reader auto-scroll tests**

```bash
./gradlew spotlessCheck :app:testDebugUnitTest \
  --tests 'eu.kanade.tachiyomi.ui.reader.viewer.webtoon.*' \
  --tests 'eu.kanade.tachiyomi.ui.reader.setting.ReaderAutoScrollPreferencesTest'
```

- [ ] **Step 2: Assemble debug after the reader changes**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 3: Perform the bounded manual smoke check when an emulator/device is available**

Verify Webtoon and Continuous Vertical show the play action and start disabled; 20/60/180 dp/s visibly differ; a tap, drag, pinch, or long press pauses without blocking its original action; paged modes show no action; reaching the final transition stops and exposes the existing menu. Record device/API and outcome in the final delivery report; do not weaken automated tests if no emulator is available.
