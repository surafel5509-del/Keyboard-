# Testing

## What was actually run

### 1. Plain-JVM domain suite (no Android, no JUnit needed)

`app/src/test/java/com/novakeyboard/domain/DomainTestSuite.kt` has a `main()`
that executes nine scenarios and exits non-zero on failure.

```bash
# exactly how it was run during development (JDK 25 + kotlinc 2.x, no Gradle):
kotlinc -cp <compiled-app-classes>:kotlin-stdlib.jar \
        app/src/test/java/com/novakeyboard/domain/DomainTestSuite.kt -d /tmp/tout
java -cp /tmp/tout:<compiled-app-classes>:kotlin-stdlib.jar \
     com.novakeyboard.domain.DomainTestSuiteKt
```

Result of the last run (`assets: app/src/main/assets`):

```
PASS  shift and caps lock
PASS  insertion and backspace
PASS  suggestions and ranking
PASS  autocorrect
PASS  preferences
PASS  language switching and Geez input
PASS  clipboard limits and capture policy
PASS  sensitive fields
PASS  emoji index

214 checks, 0 failure(s), 422 ms
ALL TESTS PASSED
```

### 2. JUnit 4 suite (what CI runs)

`DomainUnitTests.kt` wraps every scenario in a JUnit test so Gradle reports them
individually:

```bash
./gradlew :app:testDebugUnitTest
```

The task sets `-Dnova.assets=<app>/src/main/assets`, which is how the tests find
the bundled dictionary/bigram/emoji files. CI result (run 40172902985,
`:app:testDebugUnitTest`): **9 tests, 0 failures, 0 errors, 0 skipped**
(also visible as a `Unit tests` annotation on the check run).

### 3. APK verification (not a unit test, but executed)

```bash
node tools/verify_apk.js dist/nova-keyboard-debug.apk
#  sig blocks: v2=0x7109871a
#  certificate: C=ET, O=NOVA KEYBOARD, CN=NOVA KEYBOARD Debug
#  VALID: APK Signature Scheme v2 signature and digests are correct

aapt2 dump permissions dist/nova-keyboard-debug.apk   # -> no permissions
aapt2 dump badging     dist/nova-keyboard-debug.apk   # -> provides-component:'ime'
unzip -t               dist/nova-keyboard-debug.apk   # -> No errors detected
```

The verifier itself was validated against
`TestActivity_signed_both.apk` from the androguard test corpus (a real
`apksigner`-signed APK): it reports VALID for it and correctly reports “no v2
block” for a v1-only APK.

## Test coverage map

| Scenario | Asserts |
| --- | --- |
| `shiftAndCaps` | one-shot shift clears after typing, double-tap within 380 ms locks caps, caps survives typing, tap while locked releases, slow double tap does not lock, auto-capitalisation never clears caps, reset |
| `insertionAndBackspace` | word building, backspace to empty, space finishing a word, sentence flag after `.`/space/Enter, sync from editor text, undo of an autocorrect, case matching |
| `suggestionsAndRanking` | 40k-word dictionary and 20k bigram loading, exact lookup, frequency ordering, prefix limit/order, next-word prediction for “thank” → “you”, disabling prediction, emoji suggestion on/off, personal dictionary add/remove, learned-word add/forget, empty/zero-limit/unknown input |
| `autoCorrect` | `teh` → `the`, title-case preservation, correct words untouched, <3 letters untouched, acronyms/URLs/emails untouched, unknown word untouched, dictionary-not-loaded guard |
| `preferences` | defaults, persistence, preset clears custom colours, clamping (radius/gap/height/opacity/clipboard size/volume), language list validation, invalid Amharic mode fallback, restore-defaults keeps setup flag, listener notifications |
| `languagesAndGeez` | id mapping, `selam`/`enat`/`biet`/`kwa`/digraph/`sie`/punctuation transliteration (compared as code points), units round-trip for backspace, seven orders, Latin→Ethiopic mapping (`s`→U+1230, `S`→U+1238, `sz`→U+1220) |
| `clipboardLimits` | save/ordering, duplicate replacement, trimming to the max, pinned survival, lowering the limit, delete, clear, empty/blank/over-long rejection, capture policy (disabled, not opted in, sensitive field, empty, too long) |
| `sensitiveFields` | password/visible/web password, numeric PIN, `IME_FLAG_NO_PERSONALIZED_LEARNING`, private IME options, email/URI/phone/multiline kinds and actions, `NO_SUGGESTIONS` flag |
| `emojiIndex` | 1,914 entries, every entry has glyph/name/category, every category non-empty and consistent, search limits, unknown/empty query, keyword match for suggestions |

## Integration tests (not executed here)

These require a device/emulator (`adb`) which is unavailable in the environment
where this repository was written. They are listed with the exact steps so they
can be executed on any machine with an emulator; **none of them is claimed as
passing.**

| # | Test | Steps | Expected |
| --- | --- | --- | --- |
| 1 | Enable & select | Settings → Languages & input → enable *NOVA KEYBOARD* → choose it | Setup screen flips both status lines to the accent “enabled/selected” state |
| 2 | Normal text field | Open Contacts search, type `hello wrld` | Keys commit, suggestion strip shows completions, autocorrect offers corrections |
| 3 | Multiline | Notes app, press Enter twice, type | Enter inserts newlines instead of performing an action; keyboard does not cover the caret |
| 4 | Password field | Any login form | No suggestions, no autocorrect, no learning, clipboard capture skipped |
| 5 | App switching | Alt-tab between two editors | Layout/action key updates; no stale suggestions |
| 6 | Layout switching | Tap `123`, `=\<`, `ABC`, long-press letters | Symbols pages 1/2 and letters render; alternates popup appears and commits |
| 7 | Theme switching | Settings → Appearance → pick each preset; drag radius/opacity sliders | Keyboard preview updates live; returning to an app shows the new theme |
| 8 | Restart | Force-stop the keyboard, reopen an editor | Settings, theme, learned words and clipboard history survive |
| 9 | Airplane mode | Enable airplane mode, type | No difference: the app has no network code and no INTERNET permission |
| 10 | Post-reboot | Reboot, type in any app | The IME is still the selected keyboard; preferences intact |
| 11 | Geez input | Enable Amharic, type `selam` in transliteration mode | Text becomes ሰላም; backspace removes one syllable |
| 12 | Emoji & clipboard | Open panels, insert emoji, save a clip, pin/delete/clear | Emoji inserted at the cursor; clipboard actions persist across restarts |

## Running the tests yourself

```bash
./gradlew :app:testDebugUnitTest            # JUnit
open app/build/reports/tests/testDebugUnitTest/index.html
```

or without Gradle, using the `main()` entry point described above.
