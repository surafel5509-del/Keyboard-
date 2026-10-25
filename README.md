# NOVA KEYBOARD

A complete, **fully offline** Android keyboard (Input Method Editor) written in
Kotlin. No internet permission, no analytics, no cloud — the APK does not even
contain a network stack.

[![Build APK](https://github.com/surafel5509-del/Keyboard-/actions/workflows/android-build.yml/badge.svg)](https://github.com/surafel5509-del/Keyboard-/actions/workflows/android-build.yml)

| | |
| --- | --- |
| Package | `com.novakeyboard` |
| Language | Kotlin 2.0.21 (MVVM, no AndroidX/Material at runtime) |
| SDK | minSdk **26**, targetSdk **35**, compileSdk **35** |
| Permissions | **none** (verified on every build) |
| Size | debug APK ≈ 1.4 MB, minified release APK ≈ 0.43 MB |
| Build | Gradle Kotlin DSL + GitHub Actions (`Build APK` workflow) |

---

## 1. Download and install

**Latest CI build (built by GitHub Actions from this repository):**
<https://github.com/surafel5509-del/Keyboard-/releases/tag/latest-build>

* `nova-keyboard-debug.apk` — installable directly
* `nova-keyboard-release.apk` — minified R8 build
* `SHA256SUMS.txt` — checksums

Every push also stores the same APKs as the workflow artifact
`nova-keyboard-apk` on the run page.

```bash
adb install -r nova-keyboard-debug.apk
```

Then enable it once:

1. Open **NOVA KEYBOARD** (the setup screen).
2. *Step 1* → **Open keyboard settings** → enable **NOVA KEYBOARD**.
3. *Step 2* → **Choose keyboard** → pick **NOVA KEYBOARD** (you can also switch
   with the keyboard button in the navigation bar).
4. *Step 3* → type in the test field to confirm it works.

Android shows a “this keyboard can collect what you type” warning for every IME.
NOVA KEYBOARD declares **no permissions** and contains no networking code;
`docs/PRIVACY.md` explains exactly what it can and cannot do, and you can audit
the APK yourself in one command (see §6).

---

## 2. What it does

### Typing surface
* Full **QWERTY** letters layout with shift, double-tap **caps lock**, backspace
  **auto-repeat** (accelerating), space, enter, layout switch and a toolbar.
* **Symbols page 1** (digits 0–9, `@ # $ % & * - + ( )`, `/ \ " ' : ; ! ?`),
  **symbols page 2** (`` ~ ` ^ ° µ § ± × ÷ = `` / `{ } [ ] < > € £ ¥ ¢` /
  `_ | • √ π ∆ ∞ ₿`) and a dedicated **number pad** for numeric input fields.
* Optional **number row** on the letters page.
* **Long-press alternates** on every letter key (é, ñ, ü, ç, ø, å, ß …), with a
  popup you can slide across to choose.
* **Space-bar cursor swipe** moves the caret one character per ~11 dp.
* **Auto-shift / auto-capitalisation** (respects `CAP_CHARACTERS`,
  `CAP_WORDS`, `CAP_SENTENCES`).
* Smart punctuation: `'`→`’`, `--`→`—`, `...`→`…` where appropriate.
* Correct `InputConnection` usage: `commitText`, `deleteSurroundingText`,
  batch edits for replacement, `sendKeyEvent` fallback, selection deletion,
  `performEditorAction` for Search/Send/Next/Done/Go, multiline Enter.
* **Hardware keyboards keep working** — the IME never swallows physical key
  events and the input view adapts (`onEvaluateFullscreenMode() == false`).
* Layout and state survive **rotation** and input-type changes
  (`onConfigurationChanged`, `onStartInput`, `onStartInputView`).

### Themes and customisation
Six built-in presets — **Midnight Black**, **Ocean Blue**, **Violet Glow**,
**Pure White**, **Minimal Gray**, **Forest Green** — all drawn with rounded keys,
subtle gradients and shadows. Everything is adjustable in
**Settings → Appearance** with a **live keyboard preview** that re-renders as you
drag:

* background, key and text colours (custom colour picker),
* accent colour,
* corner radius, key gap, key height, background opacity,
* key borders,
* number row, suggestion strip, toolbar visibility,
* sound on/off + volume, haptics on/off,
* auto-capitalisation, autocorrect, next-word prediction, emoji suggestions,
  long-press alternates, space-bar cursor, learning,
* **Restore defaults** (keeps the “setup complete” flag).

### Suggestions, prediction, autocorrect
* **3-item suggestion strip** powered by a bundled 40,000-word
  frequency-ranked English dictionary.
* **Next-word prediction** from a bundled 20,000-entry bigram table (a
  statistical n-gram model, *not* an AI/LLM).
* **Conservative edit-distance autocorrect** (Damerau-Levenshtein distance 1)
  with frequency floors per word length; skips URLs, e-mails, acronyms and known
  words. Every correction offers **Undo** in the strip, and backspace also
  reverts it.
* **Local learning** (opt-in, capped at 1,200 words) and a **personal
  dictionary** (add / remove words from Settings).
* All work happens on a **single background thread** with a token guard; the
  main thread only swaps the resulting list.

### Amharic (Geez) — real text input
* **Transliteration mode**: type Latin and get Ethiopic syllables live
  (`selam` → ሰላም, `biet` → ቤት, `kwa` → ኳ). Handles the seven orders,
  labialised consonants, digraphs (`sh ch ts ny ng kh zh sz th dh ph`),
  stand-alone vowels and Ethiopic punctuation (`,` → ፣, `.` → ።, `?` → ፧).
  The Latin buffer is kept so backspace edits syllable by syllable.
* **Direct Geez mode**: a 5-row keyboard of the full syllabary with all seven
  orders on long-press, plus the Ethiopic punctuation row.
* Language chip in the toolbar cycles enabled languages; the system subtype
  picker also lists `English (US)` and `አማርኛ`.
* The engine is data-driven (`GeezAlphabet`), so adding Tigrinya or another
  Ethiopic language is a table addition, not a rewrite.

### Emoji
* Offline index of **1,914 emoji** in 9 categories (smileys, people, animals,
  food, activities, travel, objects, symbols, flags).
* **Local search** over names, short names and keywords, with a dedicated
  search row on the keyboard.
* **Recents** (up to 36), one-tap insert, long-press for a bigger preview.
* Optional **emoji suggestions** for exact keywords (`pizza` → 🍕).

### Clipboard (optional, local only)
* History of 5–100 entries (2,000 chars each), pinned entries survive trimming.
* Insert, pin/unpin, delete, clear — all from the clipboard panel.
* Two explicit capture paths only: **manual “save current clipboard”**, or the
  opt-in `OnPrimaryClipChangedListener`.
* Never polled, never written back, and disabled in password fields.

### Privacy and security
See **[docs/PRIVACY.md](docs/PRIVACY.md)**. Short version:

* **No permissions at all** (not even `INTERNET` or `VIBRATE`), verified by CI on
  every build with `aapt2 dump permissions`.
* No `Log.*` calls anywhere in the app; typed text is never persisted.
* Password / PIN / `IME_FLAG_NO_PERSONALIZED_LEARNING` fields get **no
  suggestions, no autocorrect, no learning, no clipboard capture**.
* `allowBackup=false` — learned words never leave the device via cloud backup.
* In-app privacy statement (Settings → Privacy → *Privacy & permissions*).

---

## 3. Project layout

```
app/src/main/java/com/novakeyboard/
├── ime/            NovaInputMethodService, KeyboardController, InputSession, ShiftState
├── ui/
│   ├── keyboard/   KeyboardView (single-Canvas renderer), layouts, key model, icons
│   ├── toolbar/    ToolbarView (emoji, clipboard, themes, settings, language)
│   ├── emoji/      EmojiIndex (search + categories), EmojiPanelView, recents
│   ├── clipboard/  ClipboardPanelView
│   ├── theme/      KeyboardTheme + 6 presets + resolver
│   ├── settings/   SettingsActivity (all options + live preview)
│   ├── setup/      SetupActivity (enable → select → test)
│   └── privacy/    PrivacyActivity
├── data/
│   ├── prefs/      KeyValueStore, AndroidKeyValueStore, Settings + SettingsRepository
│   ├── dictionary/ Dictionary, BigramModel, UserDictionary, CompositeWordSource
│   └── clipboard/  ClipboardRepository, ClipboardCapturePolicy
├── domain/
│   ├── prediction/ PredictionEngine (prefix, next-word, emoji)
│   ├── autocorrect/ AutoCorrector (edit distance + frequency floors)
│   └── language/   Language, GeezAlphabet, GeezTransliterator
├── security/       EditorState (input-type/privacy policy)
└── utils/          EditDistance, Words, Feedback (sound + haptics), Colors, Dimensions

app/src/main/assets/
├── dictionaries/en_words.txt    40,000  word<TAB>count
├── dictionaries/en_bigrams.txt  20,000  previous<TAB>next<TAB>count
└── emoji/emoji.tsv               1,914  emoji<TAB>short name<TAB>name<TAB>category<TAB>keywords
```

`docs/ARCHITECTURE.md` describes the data flow and the performance decisions
(no per-keystroke allocations in the renderer, dictionary work off the main
thread, single reusable `Paint` objects, token-guarded async suggestions).

---

## 4. Build

### GitHub Actions (recommended, no local SDK needed)
Push to any branch and the **Build APK** workflow runs:

1. `:app:testDebugUnitTest` — 9 test classes, 214 assertions;
2. `:app:assembleDebug`;
3. `:app:assembleRelease` (R8 + resource shrinking);
4. `tools/verify_apk.js` — independent APK Signature Scheme **v2** verification;
5. `aapt2 dump permissions` — fails the build if *any* permission appears;
6. uploads artifacts and refreshes the [`latest-build`](https://github.com/surafel5509-del/Keyboard-/releases/tag/latest-build) release.

### Android Studio / Gradle locally
```bash
git clone https://github.com/surafel5509-del/Keyboard-.git
cd Keyboard-
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # unit tests
./gradlew :app:assembleRelease    # minified release APK
```
Requires JDK 17 and Android SDK platform 35 + build-tools 35.0.0
(`sdkmanager "platforms;android-35" "build-tools;35.0.0"`).

### Offline / no-Gradle build
`tools/build_local.sh` reproduces the APK with only `aapt2`, `kotlinc`, `dx` and
node — this is how the `dist/nova-keyboard-debug.apk` in this repository was
produced:

```bash
AAPT2=/path/to/aapt2 \
ANDROID_JAR=/path/to/android.jar \
KOTLINC=/path/to/kotlinc \
DX_JAR=/path/to/dx.jar \
./tools/build_local.sh dist/nova-keyboard-debug.apk
node tools/verify_apk.js dist/nova-keyboard-debug.apk
```

---

## 5. Tests

Executed (all green):

| Suite | What it covers | Result |
| --- | --- | --- |
| `DomainTestSuite` (`main`, plain JVM) | shift/caps, insertion/backspace, suggestions & ranking, autocorrect, preferences, language switching & Geez, clipboard limits, sensitive fields, emoji index | **214 checks, 0 failures** |
| `DomainUnitTests` (JUnit 4, CI `:app:testDebugUnitTest`) | the same 9 scenarios as individual tests | **9 tests, 0 failures** |

`docs/TESTING.md` lists the exact commands and the integration-test matrix,
including what could **not** be executed here (no emulator/device is available in
this environment — see the honesty notes in `docs/LIMITATIONS.md`).

---

## 6. Verify the APK yourself

```bash
# 1. no permissions at all
aapt2 dump permissions app-debug.apk           # -> package contains no permissions
# 2. it really is an IME
aapt2 dump badging app-debug.apk | grep ime    # -> provides-component:'ime'
# 3. v2 signature + content digest, without trusting our signer
node tools/verify_apk.js app-debug.apk         # -> VALID: ...
```

`tools/verify_apk.js` implements the AOSP
`ApkSignatureSchemeV2Verifier`/`ApkSigningBlockUtils` algorithm (length-prefixed
records, 1 MB chunked digests, EOCD normalisation). It was cross-checked against
a real `apksigner`-signed APK, so its “VALID” verdict is meaningful for our own
builds too.

---

## 7. Data sources and licences

| Asset | Source | Licence |
| --- | --- | --- |
| `en_words.txt` | [FrequencyWords](https://github.com/hermitdave/FrequencyWords) `en_50k` (filtered to 40k) | MIT |
| `en_bigrams.txt` | [NLTK](https://github.com/nltk/nltk_data) Brown corpus bigram counts | Public domain |
| `emoji.tsv` | [iamcal/emoji-data](https://github.com/iamcal/emoji-data) | MIT |
| Key click sound | generated (sine burst) by `tools/generate_assets.py` | Public domain |

Regenerate the assets with `python3 tools/generate_assets.py` (needs network).

---

## 8. Honest limitations

* **No on-device/emulator test run was performed in the development
  environment** used to write this code (no adb, no emulator, no Android SDK
  manager). The APKs are built by CI and verified structurally
  (signature, manifest, permissions, resources, dex contents), and all pure logic
  is covered by the unit tests — but “enable the keyboard and type on a phone”
  has not been executed here. See `docs/LIMITATIONS.md` for the full list.
* Amharic has **no dictionary, no prediction and no autocorrect** — only the
  transliteration/Geez keyboards. This is deliberate and documented in-app.
* Prediction is a **bigram table**, not an AI model; it does not learn context.
* Emoji are shipped as text glyphs (no bundled colour emoji font), so they render
  with whatever emoji font the device provides.
* The release build type is signed with the committed **debug key** so that it
  installs out of the box; use your own key for a real release
  (`tools/keystore/README.md`).
* Only English and Amharic are implemented; the architecture is extensible but
  additional languages require their own alphabet/dictionary data.

---

## 9. Repository map

| Path | Contents |
| --- | --- |
| `app/` | the Android application (sources, resources, assets, unit tests) |
| `.github/workflows/android-build.yml` | CI: tests → debug/release APKs → signature + permission audit → release |
| `tools/build_local.sh` | Gradle-free build pipeline |
| `tools/sign_v2.js` | APK Signature Scheme v2 signer (JKS keystore, no apksigner needed) |
| `tools/verify_apk.js` | independent v2 verifier |
| `tools/ci_annotate.sh` | turns CI failures into readable annotations |
| `tools/generate_assets.py` | regenerates dictionaries, emoji index and the key-click sound |
| `tools/keystore/` | throw-away debug key + instructions for a real release key |
| `dist/nova-keyboard-debug.apk` | APK built from this commit with `tools/build_local.sh` |
| `docs/` | privacy, architecture, testing, limitations, third-party notices |
