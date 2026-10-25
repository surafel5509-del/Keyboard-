# Limitations and not-implemented items

This file exists so that nothing in the README overstates what the app does.
Everything listed under “Implemented” is in the code; everything under “Not
implemented” is genuinely absent.

## Verification status (be precise about this)

| What | Status |
| --- | --- |
| Kotlin sources compile | **Executed** — `kotlinc` 2.x over all 40 files with a generated `R.java`, exit 0 (locally) and `:app:assembleDebug` on CI, success. |
| Unit tests | **Executed** — 214 checks locally on a plain JVM; 9 JUnit tests on CI (`:app:testDebugUnitTest`), 0 failures. |
| Debug + release APK built | **Executed** — on GitHub Actions (1,408,148 B debug, 433,451 B release) and locally with `tools/build_local.sh` (1,290,658 B). |
| APK signature verified | **Executed** — `tools/verify_apk.js` (independent v2 verifier, itself cross-checked against a real `apksigner` APK) on both the local and the CI APKs; the verifier also ran as a CI step. |
| Manifest audited | **Executed** — `aapt2 dump badging` shows `provides-component:'ime'`, `aapt2 dump permissions` shows no permissions; CI fails on any permission. |
| **Installed and typed on a real device or emulator** | **NOT executed.** The development environment has no `adb`, no emulator and no Android SDK manager. Behaviour that only appears on a device (IME enable flow, insets, haptics, OEM quirks) is therefore *designed* and *structurally verified*, not observed. |
| Instrumented tests (`androidTest/`) | **NOT written.** Writing them here would produce code that could not be compiled or run in this environment, which would be worse than an honest gap. |
| Rotation, app switching, post-reboot, airplane mode | **NOT executed.** Rotation is handled in `onConfigurationChanged`; airplane mode is irrelevant because the app has no networking; restart persistence is covered by `SettingsRepository` tests. Treat these as untested on hardware. |

## Functional limitations

1. **Amharic has no dictionary, no suggestions, no prediction and no
   autocorrect.** The keyboards are real (transliteration + direct Geez) but the
   40k-word corpus is English only. The app says so in Settings
   (`settings_am_dictionary_note`).
2. **Prediction is a bigram model**, not a language model. It only knows which
   word followed another word in the bundled corpus (20k pairs, 8 followers per
   word). It does not use context beyond one word and does not adapt to style.
3. **Autocorrect is conservative by design** (edit distance 1 with frequency
   floors). It will leave many typos untouched rather than risk rewriting a
   correct word; there is no “aggressive” mode.
4. **Learning is capped** at 1,200 words and stores no counts beyond a derived
   frequency; there is no decay or per-app profile.
5. **No dictionary import/export** and no sync (by design: no storage permission,
   no network). Words are added one at a time in Settings.
6. **Emoji are text glyphs**, not bundled images; the look depends on the
   device's emoji font. Newer emoji may be missing on old devices.
7. **No gesture typing / swipe-to-type.** The space-bar swipe moves the cursor
   only.
8. **No voice input, no translation, no spell-check underline** (the IME has no
   `SpellCheckerSession` integration).
9. **No split/one-handed/floating keyboard** and no tablet-specific layout; the
   layout scales with key-height settings and orientation but is not split.
10. **No RTL/BiDi-specific layout.** `supportsRtl="true"` is set and text input
    works, but there is no Arabic/Hebrew keyboard.
11. **Clipboard capture depends on the OS**: Android only delivers
    `OnPrimaryClipChangedListener` callbacks while the keyboard process is alive
    and the app has focus; on Android 10+ background clipboard access is
    restricted. The manual “save current clipboard” button always works.
12. **Key sound has one sample.** No per-key sounds and no “sound on key release”
    option.
13. **Hardware keyboard support means “don't break it”**: the IME does not add
    its own handling for physical key events (the platform delivers them
    directly to the editor), so e.g. autocorrect does not run on hardware keys.

## Build / packaging caveats

* The `release` build type is signed with the **committed debug key** so the
  artifact installs out of the box. Replace it for any real distribution
  (`tools/keystore/README.md`).
* The rolling **`latest-build`** release is marked *pre-release* and is
  overwritten on every push to this branch. For a stable version, tag a commit.
* `tools/build_local.sh` links against `android-34/android.jar` (the jar that was
  available offline), so the locally built APK reports
  `compileSdkVersion 34`; the CI/Gradle build uses SDK 35. `minSdk`/`targetSdk`
  are 26/35 in both.
* The Gradle build needs network access to Google Maven on first run. In the
  environment where this repository was written, `dl.google.com` was blocked, so
  the shipped `dist` APK was produced by the manual pipeline instead; CI uses
  Gradle and succeeds.
* Only `en`/`am` resources exist; `MissingTranslation` lint is disabled.
