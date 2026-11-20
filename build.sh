#!/usr/bin/env bash
set -euo pipefail

ROOT="$HOME/ussdpay"
TC="$HOME/toolchain"
SDK="$TC/android-sdk"
JDK="$TC/jdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-30/android.jar"
OUT="$ROOT/build"

export PATH="$JDK/bin:$BT:$PATH"
export JAVA_HOME="$JDK"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== compile resources =="
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "== link resources =="
aapt2 link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

echo "== javac =="
find "$ROOT/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn \
  -bootclasspath "$PLATFORM" \
  -classpath "$TC/zxing-core.jar" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"

echo "== dex =="
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
# ZXing must be dexed into the APK (it is an input, not just a classpath entry)
d8 --lib "$PLATFORM" --min-api 30 --output "$OUT/dex" \
  $(cat "$OUT/classes.txt") "$TC/zxing-core.jar"

echo "== package =="
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex )

echo "== align =="
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign =="
KS="$TC/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/ussdpay.apk" "$OUT/aligned.apk"

echo "== done =="
ls -lh "$OUT/ussdpay.apk"
