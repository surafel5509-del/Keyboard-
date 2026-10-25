# Building NOVA KEYBOARD

Three supported paths, in order of convenience.

## 1. GitHub Actions (how the published APKs are built)

The workflow is `.github/workflows/android-build.yml`; it runs on every push and
pull request and can be triggered manually (`workflow_dispatch`).

Steps:

1. JDK 17 (Temurin), Gradle 8.9 via `gradle/actions/setup-gradle`, and the
   runner's preinstalled Android SDK (platform 35 + build-tools 35.0.0 are
   installed if missing).
2. `./gradlew :app:testDebugUnitTest` — 9 JUnit tests.
3. `./gradlew :app:assembleDebug` and `:app:assembleRelease`.
4. `node tools/verify_apk.js` on both APKs (APK Signature Scheme v2).
5. `aapt2 dump permissions` / `dump badging` audit — the job fails if any
   permission appears or the IME service is missing.
6. Uploads `nova-keyboard-apk` (APKs + `SHA256SUMS.txt`) and refreshes the
   `latest-build` pre-release.

Failures are surfaced as annotations (`tools/ci_annotate.sh` parses the Gradle
log and the JUnit XML), so they are readable from the checks API and the run
summary even without downloading logs.

## 2. Android Studio / local Gradle

Requirements:

* JDK 17 (`java -version`)
* Android SDK platform **35** and build-tools **35.0.0**
  (`sdkmanager "platforms;android-35" "build-tools;35.0.0" platform-tools`)
* Network access to `dl.google.com` and Maven Central for the first build

```bash
git clone https://github.com/surafel5509-del/Keyboard-.git
cd Keyboard-
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Outputs:

| Build | Path |
| --- | --- |
| debug | `app/build/outputs/apk/debug/app-debug.apk` |
| release (R8) | `app/build/outputs/apk/release/app-release.apk` |

Both are signed (debug/release both use the committed debug key, see
`tools/keystore/README.md`) and installable with
`adb install -r <apk>`.

## 3. Offline pipeline (no Gradle, no SDK manager)

`tools/build_local.sh` builds the same APK with just `aapt2`, `kotlinc`, `dx` and
node. This is how `dist/nova-keyboard-debug.apk` was produced in an environment
where Google Maven was unreachable.

```bash
AAPT2=/path/to/aapt2 \
ANDROID_JAR=/path/to/platforms/android-34/android.jar \
KOTLINC=/path/to/kotlinc \
DX_JAR=/path/to/dx.jar \
./tools/build_local.sh dist/nova-keyboard-debug.apk
```

What the script does:

1. `aapt2 compile` all resources → `*.flat`;
2. injects `package="com.novakeyboard"` into a temporary manifest copy (AGP 8
   forbids the attribute, `aapt2 link` requires it) and links resources,
   generating `R.java`;
3. `kotlinc` compiles `R.java` + all Kotlin sources (JVM target 1.8, class-based
   lambdas so `dx` accepts them);
4. flattens the Kotlin stdlib (dropping `META-INF`, which `dx` cannot parse) and
   runs `dx --min-sdk-version=26` to produce `classes.dex`;
5. repackages the APK (aapt2 output + `classes.dex` + `assets/`);
6. signs with `tools/sign_v2.js` (APK Signature Scheme v2, JKS keystore) — no
   `apksigner` needed.

Verify with `node tools/verify_apk.js <apk>`.

Convenience toolchain used for the checked-in APK (not vendored in the repo):

| Tool | Source |
| --- | --- |
| JDK | `pip download jdk4py` (Temurin 25 runtime, includes `keytool`) |
| `aapt2` | npm `aaptjs3` (build-tools 2.20-15009934) |
| Kotlin compiler | npm `kotlin-compiler` (2.x) |
| `android.jar` | `Sable/android-platforms` sparse clone (`android-34`) |
| `dx` | `LineageOS/android_prebuilts_build-tools` (`common/framework/dx.jar`) |
| v2 signer | `tools/sign_v2.js` + npm `apk_sign_ts` (JKS→PEM only) |

## Release checklist

- [ ] Replace the debug keystore with your own release key
      (`tools/keystore/README.md`) and update `app/build.gradle.kts`.
- [ ] Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
- [ ] Tag the commit (`git tag v1.0.0 && git push --tags`) and attach the APKs to
      a versioned release.
- [ ] Confirm `aapt2 dump permissions` reports no permissions.
- [ ] Confirm `node tools/verify_apk.js` prints VALID.
- [ ] Re-read `docs/LIMITATIONS.md` before publishing claims.
