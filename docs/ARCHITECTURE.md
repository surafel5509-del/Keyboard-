# Architecture

NOVA KEYBOARD is a single-module Android app with an MVVM-ish split: the input
method service owns a controller (the "view model"), the views below are pure
`View` subclasses that draw themselves, and everything that can be reasoned
about without Android lives in `domain/`, `data/` and `security/`.

```
                         ┌─────────────────────────────┐
android framework  ────► │ NovaInputMethodService      │
 (EditorInfo,            │  onCreateInputView()        │
  InputConnection,       │  onStartInput(View)         │
  clipboard, config)     │  onUpdateSelection()        │
                         │  onConfigurationChanged()   │
                         └──────────────┬──────────────┘
                                        │ delegates
                         ┌──────────────▼──────────────┐
                         │ KeyboardController          │  ← the "view model"
                         │  • settings + theme         │
                         │  • EditorState (privacy)    │
                         │  • InputSession (word state)│
                         │  • ShiftStateMachine        │
                         │  • worker thread            │
                         └───┬─────────┬─────────┬─────┘
             builds views    │         │         │  async data
        ┌────────────────────▼──┐  ┌───▼────┐ ┌──▼───────────────────┐
        │ ToolbarView           │  │ strip  │ │ PredictionEngine      │
        │ SuggestionStripView   │  │        │ │ AutoCorrector         │
        │ FrameLayout           │  │        │ │ Dictionary/BigramModel│
        │  ├ KeyboardView       │  │        │ │ UserDictionary        │
        │  ├ EmojiPanelView     │  │        │ │ EmojiIndex            │
        │  └ ClipboardPanelView │  │        │ │ ClipboardRepository   │
        └───────────────────────┘  └────────┘ └───────────────────────┘
```

## Layers

| Layer | Package | Responsibility |
| --- | --- | --- |
| Platform glue | `ime` | `InputMethodService` callbacks → controller. Only this package imports `android.inputmethodservice`. |
| Presentation | `ui` | Canvas views and the three activities. No business rules. |
| State | `ime/InputSession.kt`, `ime/ShiftState.kt` | Pure Kotlin word/shift state; unit-tested. |
| Policy | `security/EditorState.kt` | Translates `inputType` + `imeOptions` into layout kind, enter action and privacy flags. |
| Domain | `domain` | Prediction, autocorrect, Geez transliteration — no Android imports. |
| Data | `data` | Assets (dictionary, bigrams, emoji), preferences, clipboard, user dictionary. |
| Utilities | `utils` | Edit distance, word helpers, sound/haptics, colours, dp. |

Dependencies point inwards only: `ui` → `ime` → `domain`/`data`/`security`.
Nothing in `domain`/`data`/`security` knows about `Activity` or `View`, which is
what makes the whole test suite runnable on a plain JVM.

## Input flow (one keystroke)

1. `KeyboardView.onTouchEvent` (ACTION_DOWN) → `Listener.onKeyPressed` for the
   haptic tick + key sound; long-press timer starts.
2. ACTION_UP → `Listener.onKeyCommitted(key)`.
3. `KeyboardController` switches on `KeyType`, calls the `InputConnection`
   (`commitText`, `deleteSurroundingText`, `performEditorAction`, …) and updates
   `InputSession` and `ShiftStateMachine`.
4. `requestSuggestions()` builds a cache key
   (`currentWord \0 previousWord \0 prediction \0 correction`), bumps a token and
   posts work to the single worker thread.
5. The worker computes suggestions through `PredictionEngine` and posts the
   result back only if the token still matches (so stale results never win).
6. `SuggestionStripView` renders the immutable list; nothing is allocated per
   keystroke inside the renderer.

## Rendering and performance

* `KeyboardView` draws every key in one `onDraw` pass with pre-allocated
  `Paint`/`RectF`/`Path` objects; the background gradient is a `LinearGradient`
  shader rebuilt only when the theme or size changes.
* `onMeasure` uses a fixed row height from settings, so layout is O(keys) once
  per layout change — not per frame.
* Backspace repeat uses one `Runnable` on the main `Handler` with two speeds.
* Dictionary parsing (≈1 MB of text) happens on the worker thread at startup;
  the UI shows an empty strip until `Dictionary.isReady` is true and re-requests
  suggestions when loading finishes.
* Prefix lookup is a binary search over a sorted `Array<String>` with
  `IntArray` frequencies (no boxing); fuzzy matching enumerates the ~54·n
  one-edit candidates and binary-searches each.
* Suggestions are cached by (current word, previous word, flags) so typing the
  same word twice costs nothing.
* The clipboard and user dictionaries are small Base64/`SharedPreferences`
  payloads read lazily; nothing is polled.
* `onTrimMemory` drops the suggestion cache; `onDestroy` shuts the worker down,
  releases the `SoundPool` and unregisters the clipboard listener (no leaks).

## Threading model

| Thread | Work |
| --- | --- |
| Main | touch handling, `InputConnection` calls, drawing, posting results |
| `keyboard-worker` (single thread, daemon) | asset parsing, prediction, autocorrect, user-dictionary writes |
| `Handler(mainLooper)` | debouncing suggestion updates and clipboard panel refreshes |

No coroutines/Flow (the app has zero runtime dependencies); a single-thread
executor plus tokens is enough for this workload and keeps the APK tiny.

## Preferences

`KeyValueStore` is a 20-line interface (`SharedPreferences` in production,
`MemoryKeyValueStore` in tests). `SettingsRepository` reads a full immutable
`Settings` snapshot and notifies `Listener`s per changed key. The controller
subscribes and, on any relevant change, re-applies the theme, layout, opacity,
keyboard height, sound/haptics and clipboard trimming — this is why editing a
slider in Settings changes the live keyboard instantly.

## Adding a language

1. Add an id to `Language` and `LanguageIds`.
2. Provide an alphabet (see `GeezAlphabet`) and, if needed, a transliterator.
3. Add a layout branch in `KeyboardLayouts` (or reuse QWERTY).
4. Add dictionary/asset data if the language should get suggestions.
5. Add a subtype entry to `res/xml/method.xml` and a switch in Settings.

No other layer needs to change — `EditorState`, prediction, clipboard and themes
are language-agnostic.
