#!/usr/bin/env bash
# Downloads and compiles everything build.sh needs into ./deps (about 50 MB).
# Needs: git, a JDK (17 or newer) and python3. No Android Studio or Gradle required.
#
# Every source is pinned to an exact commit, and the two prebuilt files that end up in or
# compile the app are checked against SHA-256 hashes, so the build fails rather than
# silently picking up changed code. To update a dependency, change its commit (and hash)
# below in a reviewed commit.
#
#   - android.jar (API 35) ........ Reginer/aosp-android-jar        (compile-time only)
#   - dx (dex compiler) ........... AOSP platform/dalvik            (Apache 2.0)
#   - apksigner ................... LineageOS mirror of AOSP apksig (Apache 2.0)
#   - ARSCLib (resource encoder) .. REAndroid/ARSCLib               (Apache 2.0)
#   - Glyph Matrix SDK ............ Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
#     (Nothing's licence lets you use the SDK in your own app but not redistribute it,
#      so it is downloaded here from Nothing's own repository, never committed to this one.)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
DEPS="${DEPS:-$ROOT/deps}"
mkdir -p "$DEPS" && cd "$DEPS"

ANDROID_JAR_SHA256=60be6880d7b2b526d654460d4ce6fa81bf23efaa28b02d901d0b63a01ccf4671
GLYPH_AAR_SHA256=be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29

# fetch <url> <dir> <commit> [sparse paths...]: exactly that commit, nothing newer
fetch() {
  local url=$1 dir=$2 commit=$3; shift 3
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD)" = "$commit" ]; then return; fi
  rm -rf "$dir" && mkdir -p "$dir"
  git -C "$dir" init -q
  git -C "$dir" remote add origin "$url"
  if [ $# -gt 0 ]; then
    git -C "$dir" config core.sparseCheckout true
    git -C "$dir" sparse-checkout set --no-cone "$@" >/dev/null
    git -C "$dir" fetch -q --depth 1 --filter=blob:none origin "$commit"
  else
    git -C "$dir" fetch -q --depth 1 origin "$commit"
  fi
  git -C "$dir" -c advice.detachedHead=false checkout -q FETCH_HEAD
  [ "$(git -C "$dir" rev-parse HEAD)" = "$commit" ] || { echo "$dir: wrong commit"; exit 1; }
}

check() { # file expected-sha256
  echo "$2  $1" | sha256sum -c --quiet - || { echo "Checksum mismatch: $1"; exit 1; }
}

echo "== downloading pinned sources"
fetch https://github.com/Reginer/aosp-android-jar ajar 262f6ae931160011572a5adfe4ec6302585e8f60 /android-35/android.jar
fetch https://github.com/aosp-mirror/platform_dalvik dalvik 838bd34deecb3715b2b4006edede2ab79e42e336 /dx/src/
fetch https://github.com/LineageOS/android_tools_apksig apksig 6447768c0e8ab4385a47e7bb85e6bdba0085dced
fetch https://github.com/REAndroid/ARSCLib arsclib 089ec68b8edbadbdfc56cf6dcec94fa536a69b2a
fetch https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit glyph 999b1143a6bff0dd79db11d21b92c15c56a346b1
check ajar/android-35/android.jar "$ANDROID_JAR_SHA256"
AAR=glyph/glyph-matrix-sdk-2.0.aar
check "$AAR" "$GLYPH_AAR_SHA256"

echo "== compiling build tools"
rm -rf tools aar && mkdir -p tools/dx tools/apksig tools/arsc aar
find dalvik/dx/src -name '*.java' > dx.lst
javac -nowarn -encoding UTF-8 -d tools/dx @dx.lst
# apksigner's optional Conscrypt provider is not needed
sed -i.bak 's/Security.addProvider(new org.conscrypt.OpenSSLProvider());/\/\/ conscrypt not used/' \
  apksig/src/apksigner/java/com/android/apksigner/ApkSignerTool.java
find apksig/src/main/java apksig/src/apksigner/java -name '*.java' > apksig.lst
javac -nowarn -encoding UTF-8 -d tools/apksig @apksig.lst
find arsclib/src/main/java -name '*.java' > arsc.lst
javac -nowarn -encoding UTF-8 -d tools/arsc @arsc.lst
cp -r arsclib/src/main/resources/* tools/arsc/

echo "== unpacking the Glyph Matrix SDK"
(cd aar && unzip -o -q "../$AAR")
echo "deps ready in $DEPS"
