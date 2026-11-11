#!/usr/bin/env bash
# Run the GitDroid unit tests without Gradle.
#
# Mirrors what CI does with :app:testDebugUnitTest, but in seconds. The
# screenshot test is skipped here because it needs Robolectric's android-all
# jars, which CI downloads and this script does not.
set -uo pipefail

OUT=/tmp/opencode/tc-out
CACHE=${APICACHE:-/tmp/opencode/apicache}

if [ ! -d "$OUT" ]; then
  echo "no compiled classes — run tools/typecheck.sh first" >&2
  exit 2
fi

CP="$OUT"
CP="$CP:/tmp/opencode/rtcheck/kotlin-stdlib.jar"
CP="$CP:/tmp/opencode/coroutines-core.jar"
CP="$CP:/tmp/opencode/coroutines-test.jar"
CP="$CP:/tmp/opencode/junit.jar"
CP="$CP:/tmp/opencode/hamcrest.jar"
CP="$CP:/tmp/opencode/annotations.jar"
CP="$CP:/tmp/opencode/okio.jar"
for art in ks-json ks-core okhttp ui ui-graphics ui-unit ui-text ui-geometry ui-util \
           foundation foundation-layout animation animation-core runtime runtime-saveable \
           material3 icons-core icons-extended core core-ktx activity coil coil-base \
           zxing lifecycle-common lifecycle-runtime uitest uitest-core; do
  # A dependency can live in three places: the unpacked AAR's classes.jar,
  # a plain jar in the cache, or a loose jar beside it.
  for j in "$CACHE/$art/classes.jar" "$CACHE/$art.jar" "/tmp/opencode/$art.jar"; do
    if [ -f "$j" ]; then CP="$CP:$j"; break; fi
  done
done

CLASSES=$(cd "$OUT" && find . -name '*Test.class' -not -path '*screenshot*' \
  | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
if [ -z "$CLASSES" ]; then
  echo "no test classes — run tools/typecheck.sh first"
  exit 1
fi

java -cp "$CP" org.junit.runner.JUnitCore $CLASSES
