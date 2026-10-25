#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Offline/manual build of NOVA KEYBOARD (no Gradle, no Android SDK manager).
#
# GitHub Actions is the canonical build (see .github/workflows/android-build.yml).
# This script exists for environments where Gradle cannot reach Google Maven and
# documents the exact toolchain the shipped APK was produced with.
#
# Required tools (paths overridable with environment variables):
#   AAPT2      aapt2 from Android build-tools
#   ANDROID_JAR platforms/android-34/android.jar (or newer)
#   KOTLINC    Kotlin 2.x compiler
#   DX         dx.jar from Android build-tools (<= 30) or com.android.tools:dx
#   SIGNER     tools/sign_v2.js + node + the `apk_sign_ts` npm package
#
# Usage: AAPT2=... ANDROID_JAR=... KOTLINC=... DX_JAR=... ./tools/build_local.sh [out.apk]
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAPT2="${AAPT2:?set AAPT2=/path/to/aapt2}"
ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR=/path/to/android.jar}"
KOTLINC="${KOTLINC:?set KOTLINC=/path/to/kotlinc}"
DX_JAR="${DX_JAR:-}"
JAVA_BIN="${JAVA_BIN:-java}"
OUT_APK="${1:-$ROOT/dist/nova-keyboard-debug.apk}"
WORK="${WORK:-/tmp/nova-build}"
PACKAGE="com.novakeyboard"
PACKAGE_DIR="com/novakeyboard"

rm -rf "$WORK"
mkdir -p "$WORK/res" "$WORK/gen" "$WORK/classes" "$WORK/dex" "$WORK/apk" "$(dirname "$OUT_APK")"

echo "==> 1/6 compiling resources"
"$AAPT2" compile --dir "$ROOT/app/src/main/res" -o "$WORK/res"

# aapt2 (unlike AGP 8) needs the package attribute, so build a temporary manifest.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'"$PACKAGE"'">|' \
  "$ROOT/app/src/main/AndroidManifest.xml" > "$WORK/AndroidManifest.xml"

echo "==> 2/6 linking resources"
"$AAPT2" link \
  -o "$WORK/apk/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$WORK/AndroidManifest.xml" \
  --java "$WORK/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code 1 \
  --version-name 1.0.0 \
  "$WORK"/res/*.flat

echo "==> 3/6 compiling Kotlin"
"$KOTLINC" -jvm-target 1.8 -Xlambdas=class -Xsam-conversions=class -no-reflect -nowarn \
  -cp "$ANDROID_JAR" \
  "$WORK/gen/$PACKAGE_DIR/R.java" \
  $(find "$ROOT/app/src/main/java" -name '*.kt') \
  -d "$WORK/classes"

echo "==> 4/6 building classes.dex"
STDLIB="$(dirname "$(readlink -f "$KOTLINC")")/../lib/kotlin-stdlib.jar"
mkdir -p "$WORK/stdlib/x"
( cd "$WORK/stdlib/x" && unzip -oq "$STDLIB" )
# dx cannot read multi-release module-info descriptors, so drop META-INF.
rm -rf "$WORK/stdlib/x/META-INF"
( cd "$WORK/stdlib/x" && zip -qr "$WORK/stdlib/stdlib-flat.jar" . )
if [ -z "$DX_JAR" ]; then echo "warning: DX_JAR not set, reusing the previous classes.dex"; else
  "$JAVA_BIN" -cp "$DX_JAR" com.android.dx.command.Main --dex \
    --min-sdk-version=26 \
    --output="$WORK/dex/classes.dex" "$WORK/classes" "$WORK/stdlib/stdlib-flat.jar"
fi

echo "==> 5/6 packaging"
cp "$WORK/apk/app-unsigned.apk" "$WORK/apk/base.apk"
( cd "$WORK/dex" && zip -q "$WORK/apk/base.apk" classes.dex )
( cd "$ROOT/app/src/main" && zip -qr "$WORK/apk/base.apk" assets )

echo "==> 6/6 signing"
SIGNER="${SIGNER:-$ROOT/tools/sign_v2.js}"
KEYSTORE="${KEYSTORE:-$ROOT/tools/keystore/nova-debug.jks}"
KEYPASS="${KEYPASS:-novakeyboard}"
node "$SIGNER" "$WORK/apk/base.apk" "$KEYSTORE" "$KEYPASS" "$OUT_APK"

echo "APK written to $OUT_APK"
