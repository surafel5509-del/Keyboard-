# Debug signing key

`nova-debug.jks` is a **throw-away debug keystore** that is committed on purpose so
that `tools/build_local.sh` / CI can produce an installable APK without secrets.

| Field | Value |
| --- | --- |
| file | `tools/keystore/nova-debug.jks` (JKS) |
| store password | `novakeyboard` |
| key password | `novakeyboard` |
| alias | `novakeyboard` |
| algorithm | RSA 2048, validity ~30 years |
| distinguished name | `CN=NOVA KEYBOARD Debug, O=NOVA KEYBOARD, C=ET` |

**Do not publish anything signed with this key.** For a real release, generate your
own key and point Gradle at it:

```bash
keytool -genkeypair -keystore nova-release.jks -alias nova \
        -keyalg RSA -keysize 4096 -validity 10950
```

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("NOVA_KEYSTORE") ?: "nova-release.jks")
            storePassword = System.getenv("NOVA_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("NOVA_KEY_ALIAS") ?: "nova"
            keyPassword = System.getenv("NOVA_KEY_PASSWORD")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
}
```

The current `release` build type falls back to the debug key so that
`./gradlew :app:assembleRelease` always produces an installable artifact.
