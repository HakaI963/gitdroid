#!/usr/bin/env bash
# Type-check the GitDroid sources without Gradle.
#
# This does NOT build the APK — GitHub Actions is the official build (see
# .github/workflows/android.yml). It compiles the Kotlin sources against the
# same dependency versions CI resolves, so a type error surfaces in seconds
# instead of after a CI round-trip.
#
# Prerequisites (all cached under /tmp/opencode):
#   - kotlin-compiler-embeddable + stdlib/reflect/script-runtime/daemon/trove
#   - the Compose compiler plugin and the kotlinx-serialization plugin
#   - android.jar for API 36
#   - the dependency classes.jar files under apicache/ (built by apifetch.py)
#   - a stub R class under rstub/out, because AGP normally generates it
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE=${APICACHE:-/tmp/opencode/apicache}
KC=/tmp/opencode/kce.jar
KSTD=/tmp/opencode/rtcheck/kotlin-stdlib.jar
KREF=/tmp/opencode/kotlin-reflect.jar
KSCRIPT=/tmp/opencode/kotlin-script-runtime.jar
KDAEMON=/tmp/opencode/kotlin-daemon-embeddable.jar
TROVE=/tmp/opencode/trove4j.jar
COR=/tmp/opencode/coroutines-core.jar
ANN=/tmp/opencode/annotations.jar
CCP=/tmp/opencode/rtcheck/cc-2.2.21.jar
SER=/tmp/opencode/ser-plugin.jar
AJAR=/tmp/opencode/p36/android-36/android.jar
OUT=/tmp/opencode/tc-out

if [ ! -f "$KC" ] || [ ! -d "$CACHE" ]; then
  echo "toolchain not cached under /tmp/opencode — run the CI build instead" >&2
  exit 2
fi

CP="$AJAR:$COR:$ANN:/tmp/opencode/rstub/out"
for art in material3 ui ui-graphics ui-text ui-unit ui-geometry ui-util \
           ui-tooling ui-tooling-preview \
           foundation foundation-layout animation animation-core \
           runtime runtime-saveable \
           icons-core icons-extended navigation \
           lifecycle-vm-compose lifecycle-runtime-compose lifecycle-common \
           lifecycle-runtime lifecycle-viewmodel lifecycle-viewmodel-savedstate \
           savedstate activity activity-ktx activity-compose core core-ktx \
           datastore coil coil-base coil-compose-base okhttp ks-json ks-core work-runtime zxing \
           uitest uitest-core test-ext-junit test-core; do
  # A dependency can live in three places: the unpacked AAR's classes.jar,
  # a plain jar in the cache, or a loose jar beside it.
  for j in "$CACHE/$art/classes.jar" "$CACHE/$art.jar" "/tmp/opencode/$art.jar"; do
    if [ -f "$j" ]; then CP="$CP:$j"; break; fi
  done
done
CP="$CP:$KSTD:/tmp/opencode/coroutines-test.jar:/tmp/opencode/junit.jar:/tmp/opencode/hamcrest.jar"
CP="$CP:/tmp/opencode/robolectric.jar:/tmp/opencode/robo-annotations.jar:/tmp/opencode/robo-sandbox.jar"
CP="$CP:/tmp/opencode/robo-utils.jar:/tmp/opencode/robo-junit.jar"
CP="$CP:/tmp/opencode/roborazzi.jar:/tmp/opencode/roborazzi-compose.jar"

rm -rf "$OUT" && mkdir -p "$OUT"

SRC=$(find "$ROOT/app/src/main/java" "$ROOT/app/src/test/java" -name '*.kt' 2>/dev/null | sort)
[ -z "$SRC" ] && { echo "no sources"; exit 0; }

java -Xmx3g -cp "$KC:$KSTD:$KREF:$KSCRIPT:$KDAEMON:$TROVE:$COR:$ANN" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -nowarn \
  -Xplugin="$CCP" \
  -Xplugin="$SER" \
  -jvm-target 17 \
  -classpath "$CP" \
  -d "$OUT" \
  $SRC 2>&1 | grep -v "^warning:" | grep -v "^info:"
