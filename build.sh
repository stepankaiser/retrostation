#!/usr/bin/env bash
#
# RetroStation Launcher — Zero-Gradle Android Build
#
# This project is built entirely without Gradle, using raw Android SDK tools.
# Why? Because it ships a single Activity with a WebView — no Gradle overhead needed.
#
# Prerequisites:
#   - Android SDK with build-tools 36.1.0 (or adjust TOOLS version below)
#   - Java 17+ (JDK, not JRE)
#   - android.jar from platforms/android-34
#   - A debug keystore (or create one with: keytool -genkey -v -keystore debug.keystore \
#       -storepass android -alias androiddebugkey -keypass android -keyalg RSA -validity 10000)
#
# Usage:
#   ./build.sh          # Build debug APK
#   ./build.sh install  # Build and install to connected device via ADB

set -euo pipefail

# ─── Config ──────────────────────────────────────────────────────────────────
SDK="$HOME/Library/Android/sdk"
TOOLS="$SDK/build-tools/36.1.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"

PKG="com.retrohandheld.launcher"
OUT="build"

# ─── Clean ───────────────────────────────────────────────────────────────────
echo "🧹 Cleaning previous build..."
rm -rf "$OUT" classes dex compiled
mkdir -p "$OUT" classes dex compiled

# ─── Step 1: Compile resources ───────────────────────────────────────────────
echo "📦 Compiling resources..."
"$TOOLS/aapt2" compile --dir res -o compiled/

# ─── Step 2: Link resources & generate R.java ────────────────────────────────
echo "🔗 Linking resources..."
"$TOOLS/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --java src/com/retrohandheld/launcher/ \
  compiled/*.flat

# ─── Step 3: Compile Java sources ────────────────────────────────────────────
echo "☕ Compiling Java..."
SOURCES=$(find src -name "*.java")
javac \
  -classpath "$PLATFORM" \
  -sourcepath src \
  -d classes \
  -source 11 -target 11 \
  $SOURCES

# ─── Step 4: Convert to DEX ─────────────────────────────────────────────────
echo "🔄 Converting to DEX..."
"$TOOLS/d8" \
  --output dex/ \
  --lib "$PLATFORM" \
  $(find classes -name "*.class")

# ─── Step 5: Package APK ────────────────────────────────────────────────────
echo "📱 Packaging APK..."
cp "$OUT/base.apk" "$OUT/retrohandheld.unsigned.apk"
cd dex && zip -u "../$OUT/retrohandheld.unsigned.apk" classes.dex && cd ..

# ─── Step 6: Align ──────────────────────────────────────────────────────────
echo "📐 Aligning APK..."
"$TOOLS/zipalign" -f 4 \
  "$OUT/retrohandheld.unsigned.apk" \
  "$OUT/retrohandheld.aligned.apk"

# ─── Step 7: Sign ───────────────────────────────────────────────────────────
echo "🔏 Signing APK..."
"$TOOLS/apksigner" sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias debug \
  --out "$OUT/retrohandheld.apk" \
  "$OUT/retrohandheld.aligned.apk"

echo ""
echo "✅ Build complete: $OUT/retrohandheld.apk"

# ─── Optional: Install ──────────────────────────────────────────────────────
if [[ "${1:-}" == "install" ]]; then
  echo "📲 Installing to device..."
  adb install -r "$OUT/retrohandheld.apk"
  echo "🚀 Launching..."
  adb shell am start -n "$PKG/.MainActivity"
fi
