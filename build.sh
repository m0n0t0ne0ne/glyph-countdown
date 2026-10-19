#!/usr/bin/env bash
# Builds out/GlyphCountdown.apk without Gradle: javac -> dx -> ARSCLib -> zip align -> apksigner.
# Run ./setup-deps.sh once first.
#
# Signing: set KEYSTORE_PASS (and optionally KEYSTORE, default ./release.jks).
# If the keystore does not exist it is created. Keep it private and backed up:
# updates must be signed with the same key, and anyone holding it could sign a fake update.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
DEPS="${DEPS:-$ROOT/deps}"
ANDROID_JAR="$DEPS/ajar/android-35/android.jar"
GLYPH_JAR="$DEPS/aar/classes.jar"
OUT="$ROOT/out"
KEYSTORE="${KEYSTORE:-$ROOT/release.jks}"
: "${KEYSTORE_PASS:?Set KEYSTORE_PASS to your keystore password (see README)}"

[ -f "$GLYPH_JAR" ] || { echo "Missing deps — run ./setup-deps.sh first"; exit 1; }
rm -rf "$OUT" && mkdir -p "$OUT/classes"

echo "== javac"
find "$APP/src" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -cp "$GLYPH_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== dx"
java -cp "$DEPS/tools/dx" com.android.dx.command.Main --dex --min-sdk-version=26 \
  --output="$OUT/classes.dex" "$OUT/classes" "$GLYPH_JAR"

echo "== licences bundled in the app"
{ echo "Doto — SIL Open Font License 1.1"; cat "$APP/assets/fonts/OFL-Doto.txt"; echo
  echo "Space Mono — SIL Open Font License 1.1"; cat "$APP/assets/fonts/OFL-SpaceMono.txt"; echo
  echo "Glyph Matrix SDK — Nothing Technology Limited"; cat "$DEPS/glyph/LICENSE.md"; } > "$OUT/LICENSES.txt"

echo "== assemble"
javac -nowarn -cp "$DEPS/tools/arsc" -d "$OUT/builder" "$ROOT/tools/ApkBuilder.java"
mkdir -p "$OUT/app" && cp -r "$APP/AndroidManifest.xml" "$APP/res" "$OUT/app/"
mkdir -p "$OUT/app/assets/fonts" && cp "$APP"/assets/fonts/*.ttf "$OUT/app/assets/fonts/"
java -cp "$DEPS/tools/arsc:$OUT/builder" ApkBuilder "$OUT/app" "$OUT/classes.dex" "$OUT/unsigned.apk" "$OUT/LICENSES.txt"

echo "== align"
python3 "$ROOT/tools/zipalign.py" "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
if [ ! -f "$KEYSTORE" ]; then
  # CI must sign with the real key from repository secrets, never a fresh one
  [ "${REQUIRE_KEYSTORE:-0}" = "1" ] && { echo "No keystore at $KEYSTORE"; exit 1; }
  echo "Creating a new signing key at $KEYSTORE"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -alias glyph -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Glyph Countdown" >/dev/null 2>&1
fi
java -cp "$DEPS/tools/apksig" com.android.apksigner.ApkSignerTool sign \
  --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASS --ks-key-alias glyph --min-sdk-version 33 \
  --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/GlyphCountdown.apk" "$OUT/aligned.apk"
java -cp "$DEPS/tools/apksig" com.android.apksigner.ApkSignerTool verify "$OUT/GlyphCountdown.apk"
echo "Built $OUT/GlyphCountdown.apk"
