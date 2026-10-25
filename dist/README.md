# dist

`nova-keyboard-debug.apk` was built from this repository with the Gradle-free
pipeline in `tools/build_local.sh` (aapt2 → kotlinc → dx → v2 signing), because
Google Maven was unreachable from the build environment. The canonical,
reproducible builds are produced by GitHub Actions and attached to the
[`latest-build` release](https://github.com/surafel5509-del/Keyboard-/releases/tag/latest-build).

| | |
| --- | --- |
| file | `nova-keyboard-debug.apk` |
| size | 1293346 bytes |
| sha256 | `3d95da07ee2925768c64bbe87b9a3f17bfe77b744fb1a11760cd780be9a1193b` |
| package | `com.novakeyboard` (versionCode 1, versionName 1.0.0) |
| minSdk / targetSdk | 26 / 35 |
| signature | APK Signature Scheme v2 (debug key in `tools/keystore/`) |

Verify it yourself:

```bash
node tools/verify_apk.js dist/nova-keyboard-debug.apk   # -> VALID
aapt2 dump permissions dist/nova-keyboard-debug.apk     # -> no permissions
unzip -t dist/nova-keyboard-debug.apk                   # -> No errors detected
```

Install with `adb install -r dist/nova-keyboard-debug.apk`.

The APK is intentionally **not** tracked by git (`.gitignore` contains
`dist/*.apk`); download the CI build from the release page instead.
