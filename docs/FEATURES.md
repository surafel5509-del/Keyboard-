# Feature list — implemented and working

Everything below is implemented in `app/src/main/java/com/novakeyboard` and is
reachable from the UI. There are no mock screens, dead switches or “coming soon”
placeholders.

## 1. System integration

| Feature | Where | Notes |
| --- | --- | --- |
| Real `InputMethodService` | `ime/NovaInputMethodService.kt` | Declared in the manifest with `BIND_INPUT_METHOD`, `<meta-data android:name="android.view.im">` → `res/xml/method.xml`. Shows up in *Settings → System → Languages & input → On-screen keyboards*. |
| Input-method metadata + subtypes | `res/xml/method.xml` | Subtypes `en_US` (label “English (US)”) and `am` (label “አማርኛ”), `supportsSwitchingToNextInputMethod`. |
| System keyboard picker | `ui/setup/SetupActivity.kt` | “Choose keyboard” calls `InputMethodManager.showInputMethodPicker()`; the toolbar language chip falls back to the picker when only one language is enabled. |
| Setup flow | `ui/setup/SetupActivity.kt` | Enable → select → live test field; state re-read in `onResume` so it is always accurate. |
| Settings screen | `ui/settings/SettingsActivity.kt` | Every option with live preview, restore defaults, privacy and dictionary sections. |
| Privacy screen | `ui/privacy/PrivacyActivity.kt` | In-app statement of permissions and data handling. |
| Rotation | `onConfigurationChanged` | Re-applies theme, key height (44 dp landscape / 54 dp portrait × user scale) and layout. |
| Input types | `security/EditorState.kt` + `KeyboardLayouts` | TEXT, EMAIL, URI, PHONE, NUMBER, DATETIME layouts; password detection; IME actions. |
| Hardware keyboards | `NovaInputMethodService.onKeyDown` | Physical keys are never intercepted; the on-screen view can be hidden by the system without breaking input. |

## 2. Layouts

* **Letters** — 3 QWERTY rows + shift / symbols / language / comma / space /
  emoji / clipboard / enter row, optional number row, long-press alternates.
* **Symbols 1** — 0–9, `@ # $ % & * - + ( )`, `/ \ " ' : ; ! ?`.
* **Symbols 2** — `` ~ ` ^ ° µ § ± × ÷ = ``, `{ } [ ] < > € £ ¥ ¢`,
  `_ | • √ π ∆ ∞ ₿`.
* **Number pad** — 0–9 with `,` `.` and Enter (for `TYPE_CLASS_NUMBER`).
* **Phone / e-mail / URI** — the letters layout plus the matching `@ .com / :`
  keys and enter action.
* **Geez (direct)** — 5 rows × 7 consonants, all seven orders on long-press,
  plus `፣ ፤ ፥ ፧ ።`.

## 3. Typing behaviour

* One-shot shift, **double-tap caps lock**, caps lock released by another tap.
* **Backspace repeat** (70 ms, accelerating to 38 ms) with haptic tick.
* **Long-press popup alternates** with slide-to-select (haptic + highlight).
* **Space-bar cursor swipe** (one character per ~11 dp, respects RTL-less text
  cursors by sending DPAD events and resyncing).
* **Auto-shift** from sentence endings, `CAP_*` flags and after Enter.
* **Autocorrect undo**: the strip shows “Undo” and Backspace reverts the last
  correction (including a following space).
* Selection replacement: typing or backspace deletes the selection first.
* Enter key becomes **Search / Send / Next / Done / Go** per `imeOptions`, and
  `performEditorAction` is used when available.
* Smart punctuation and case preservation in suggestions (`teh` → `The`).

## 4. Themes

Six presets (`ui/theme/KeyboardTheme.kt`): Midnight Black, Ocean Blue, Violet
Glow, Pure White, Minimal Gray, Forest Green. Each defines background gradient,
key colour, pressed-key colour, text, secondary text, accent, on-accent, border,
shadow and popup colours.

Customisation (all persisted and applied live):
key radius 0–24 dp, key gap 0–8 dp, key height ×0.75–1.4, background opacity
0.55–1.0, key borders, number row, suggestion strip, toolbar, six theme presets
and four custom colours, sound + volume, haptics, auto-capitalisation,
autocorrect, prediction, emoji suggestions, long-press alternates, space-bar
cursor, learning, clipboard settings, languages, Amharic mode, restore defaults.

## 5. Suggestions and correction

* Prefix completions ranked by corpus frequency (binary search over 40k words).
* Next-word bigram predictions (20k pairs, max 8 followers per word).
* Emoji suggestions for exact keyword matches.
* Personal dictionary (user words rank highest) and learned words (2,000 + 40 ×
  usage, capped at 1,200 entries).
* Autocorrect: Damerau-Levenshtein distance 1, frequency floors
  (3 letters ≥ 6000, 4 ≥ 3000, 5–7 ≥ 900, 8+ ≥ 400), URL/e-mail/acronym
  guards, known-word guard.
* Undo via strip item or Backspace; disabled in sensitive fields.
* Everything runs on one worker thread; results are token-checked before they
  touch the UI.

## 6. Amharic

* Transliteration engine with 34 consonant series, labialised forms, digraphs,
  all seven orders and Ethiopic punctuation.
* Two keyboards: Latin transliteration (live re-render of the word being typed,
  syllable-wise backspace) and direct Geez.
* Language switching through the toolbar chip, the space-bar long-press and the
  system subtype picker; enabled languages are configurable in Settings.

## 7. Emoji

1,914 bundled emoji, 9 categories, recents (36), search by name/short name/
keyword, one-tap insert, category tabs with glyph chips, search row on the
keyboard that reuses the suggestion strip.

## 8. Clipboard

History panel with insert, pin, delete, clear, manual “save current clipboard”,
configurable size (5–100), 2,000-character limit per entry, pinning survives
trimming, opt-in automatic capture, never active in password fields.

## 9. Feedback

Bundled `key_click.wav` played through a `SoundPool` (3 streams, sonification
usage) with volume control and a fallback to the system key click; haptics via
`performHapticFeedback(KEYBOARD_TAP)` and `LONG_PRESS`.

## 10. Accessibility

* Key labels are drawn with contrast-aware colours from the theme
  (`onAccent` for accent surfaces, `textPrimary`/`textSecondary` elsewhere).
* Every custom view exposes `contentDescription` and reports the touched key
  through `announceForAccessibility` when enabled.
* Minimum 46 dp touch targets for toolbar/settings buttons (keys are ≥ 44 dp in
  landscape, 54 dp × scale in portrait).
* State is never conveyed by colour alone: shift/caps also change the glyph and
  are announced; toolbar buttons show a filled chip when active.
* Text scaling is respected in the settings/setup/privacy screens (sp-based
  text sizes via `Ui.textView`).
