# Privacy & permissions — NOVA KEYBOARD

NOVA KEYBOARD is an offline keyboard. It has no network code and declares no
Android permissions, so it cannot send anything anywhere even if it wanted to.

## 1. Permissions declared by the app

**None.** The merged manifest contains no `<uses-permission>` entry at all.

| Permission | Declared? | Why |
| --- | --- | --- |
| `android.permission.INTERNET` | **No** | The app has no networking code and no HTTP client on its classpath. |
| `ACCESS_NETWORK_STATE` | No | — |
| `READ_CONTACTS` / `READ_SMS` | No | Not needed and never requested. |
| `RECORD_AUDIO` | No | Key sounds are bundled WAV assets played through `SoundPool`; voice input is not implemented. |
| `ACCESS_FINE_LOCATION` / `COARSE` | No | — |
| `VIBRATE` | No | Haptics use `View.performHapticFeedback`, which does not require the permission. |
| `READ_CLIPBOARD` | No | Not a real permission on modern Android; clipboard reads are limited by the platform to the focused app and are opt-in (see §5). |

The input method only uses the framework permission
`android.permission.BIND_INPUT_METHOD`, which is declared **on the service** (as
Android requires) and is held by the system, not granted to the app.

You can verify this yourself on a built APK:

```bash
aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
# -> "package contains no permissions" (or an empty list)
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | grep uses-permission
# -> no output
```

The CI workflow runs exactly this check on every build and fails if any
permission appears.

## 2. What the keyboard can see

Like every Android IME, NOVA KEYBOARD receives the text of the field it is
attached to (that is how any keyboard works). What matters is what it does with
it:

* **Nothing is transmitted.** There is no socket, no `HttpURLConnection`, no
  WebView, no analytics SDK, no crash reporter.
* **Nothing is logged.** The codebase contains no `Log.*` calls at all. Typed
  text is never written to logcat, to a file, or to a database.
* **Typed text is kept in memory only** for the duration of the word being
  typed, so the suggestion strip and autocorrect can work.

## 3. Password and sensitive fields

When the editor is a password field — `TYPE_TEXT_VARIATION_PASSWORD`,
`VISIBLE_PASSWORD`, `WEB_PASSWORD`, or a numeric PIN — or when the app sets
`IME_FLAG_NO_PERSONALIZED_LEARNING` / the `noPersonalizedLearning` private IME
option, the keyboard:

* shows **no suggestions** and no prediction,
* **never autocorrects**,
* **never learns** or stores the words,
* **never captures the clipboard** while the field is focused,
* does not add anything to the personal dictionary.

This logic lives in `security/EditorState.kt` and is unit-tested in
`app/src/test/.../DomainTestSuite.kt` (`sensitiveFields`).

## 4. Learning and prediction data

* Autocorrect and next-word prediction use **bundled** assets
  (`app/src/main/assets/dictionaries/`), generated from public-domain / MIT
  corpora. They are read-only.
* Optional **learning** stores at most 1,200 words you actually typed into
  `SharedPreferences` inside the app's private directory
  (`/data/data/com.novakeyboard/shared_prefs/`). It can be turned off in
  Settings → Typing → *Learn new words*, and wiped with *Clear learned words*.
* Android backup is disabled (`android:allowBackup="false"`), so learned words
  never leave the device through Google backup.
* The personal dictionary is a plain list you manage yourself
  (Settings → Dictionary).

## 5. Clipboard

The system clipboard is **never monitored**. There are two explicit paths:

1. **Manual save** — tap the clipboard icon in the panel and press *Save current
   clipboard*. A single read happens at that moment, on your request.
2. **Optional capture** (off by default) — if you enable
   *Settings → Privacy → Capture clipboard*, the keyboard registers
   `ClipboardManager.OnPrimaryClipChangedListener` and copies new clipboard text
   into its own history. This listener only fires while the keyboard process is
   alive and the keyboard is the active input method.

In both cases: history is stored locally, capped (5–100 entries, 2,000
characters per entry), can be pinned, deleted or cleared, and is **never**
written back to the system clipboard by the app. Capture is disabled entirely
while a password field is focused, and text longer than 4,000 characters is
ignored.

## 6. Emoji

The emoji picker is a bundled index (`assets/emoji/emoji.tsv`, 1,914 entries)
with local search. Recents are stored in private preferences and can be cleared.

## 7. Backup and data removal

`android:allowBackup="false"` and `android:fullBackupContent="false"` mean
Android will not back up anything from this app (including learned words and
clipboard history).

To erase everything the keyboard stored:

1. Settings → Privacy → *Clear clipboard history* and *Clear learned words*, or
2. Android Settings → Apps → NOVA KEYBOARD → Storage → **Clear data**, or
3. uninstall the app.

## 8. Third-party code

The app depends on **no third-party runtime libraries**. There is no AndroidX,
no Material Components, no advertising SDK, no analytics and no network stack.
The only build-time dependency is the Kotlin standard library, which is bundled
into the APK.

## 9. Contact and reporting

Privacy issues can be reported through the repository's issue tracker. Please do
not paste personal text into an issue.
