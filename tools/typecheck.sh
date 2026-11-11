#!/usr/bin/env bash
# Fast local type-check of the GitDroid sources.
#
# This does NOT build the APK — GitHub Actions is the official build (see
# .github/workflows/build.yml). It only type-checks the Kotlin sources against
# the same dependency versions the CI uses, so compile errors surface in
# seconds instead of a CI round-trip.
set -uo pipefail

ROOT=/home/flux/gitdroid
CACHE=/tmp/opencode/apicache
KC=/tmp/opencode/kce.jar
KSTD=/tmp/opencode/rtcheck/kotlin-stdlib.jar
KREF=/tmp/opencode/kotlin-reflect.jar
KSCRIPT=/tmp/opencode/kotlin-script-runtime.jar
KDAEMON=/tmp/opencode/kotlin-daemon-embeddable.jar
TROVE=/tmp/opencode/trove4j.jar
ANN=/tmp/opencode/annotations.jar
COR=/tmp/opencode/coroutines-core.jar
CCP=/tmp/opencode/rtcheck/cc-2.2.21.jar
SER=/tmp/opencode/ser-plugin.jar
AJAR=/tmp/opencode/p36/android-36/android.jar
OUT=/tmp/opencode/tc-out

CP="$AJAR:$COR:$ANN"
for art in material3 ui ui-graphics ui-text foundation animation animation-core \
           icons-extended navigation runtime runtime-saveable lifecycle-vm-compose lifecycle-runtime-compose \
           activity-compose core-ktx datastore coil coil-base okhttp ks-json ks-core \
           work-runtime zxing; do
  j="$CACHE/$art/classes.jar"
  [ -f "$j" ] && CP="$CP:$j"
done
CP="$CP:$KSTD"

rm -rf "$OUT" && mkdir -p "$OUT"

SRC=$(find "$ROOT/app/src/main/java" -name '*.kt' | sort)
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
