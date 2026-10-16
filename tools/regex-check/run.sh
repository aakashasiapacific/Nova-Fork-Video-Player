#!/usr/bin/env bash
# Compiles every regex the app builds with ICU, the engine behind java.util.regex on Android, and
# fails when ICU rejects one. Run after the Gradle build (it reads the compiled classes).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
work=$(mktemp -d)

lib=$(ls /usr/lib/x86_64-linux-gnu/libicuuc.so.[0-9]* /lib/x86_64-linux-gnu/libicuuc.so.[0-9]* 2>/dev/null | head -1 || true)
if [ -z "$lib" ]; then echo "regex-check: ICU not found, skipped"; exit 0; fi
ver=${lib##*.so.}; ver=${ver%%.*}; dir=$(dirname "$lib")
gcc -O1 -DICU_VER="$ver" -o "$work/icucheck" "$here/icucheck.c" "$dir/libicui18n.so.$ver" "$dir/libicuuc.so.$ver"

# Runtime dependencies the classes need to load (static initializers run).
cp=""
for jar in kotlin-stdlib-2 kotlinx-coroutines-core-jvm kotlinx-serialization-core-jvm kotlinx-serialization-json-jvm okhttp-4 okio-jvm; do
  found=$(find "$HOME/.gradle/caches/modules-2/files-2.1" -name "$jar*.jar" ! -name "*sources*" 2>/dev/null | head -1 || true)
  [ -n "$found" ] && cp="$cp:$found"
done
android=$(ls "${ANDROID_HOME:-/nonexistent}"/platforms/*/android.jar 2>/dev/null | sort | tail -1 || true)
[ -n "$android" ] && cp="$cp:$android"

javac -d "$work" -cp "${cp#:}" "$here/Dump.java"
count=$(java -cp "$work$cp" Dump "$work/patterns.bin" \
  "$repo/core/build/classes/kotlin/main" \
  "$repo/app/build/tmp/kotlin-classes/release" \
  "$repo/app/build/tmp/kotlin-classes/debug")
echo "regex-check: $count patterns found (ICU $ver)"
# The parser alone has dozens; finding almost none means the classes didn't load.
if [ "$count" -lt 30 ]; then echo "regex-check: too few patterns found, check the classpath"; exit 1; fi
"$work/icucheck" "$work/patterns.bin"
