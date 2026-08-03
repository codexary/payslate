#!/bin/sh
# Replay every saved crash against the ASan/UBSan build and group it by finding.
set -eu
cd "$(dirname "$0")/.."
img=aflplusplus/aflplusplus:v5.02c
docker run --rm -v "$PWD:/src" -w /src \
  -e ASAN_OPTIONS=abort_on_error=1:detect_leaks=0 \
  -e UBSAN_OPTIONS=halt_on_error=1:abort_on_error=1 \
  "$img" sh -c '
    make afl >/dev/null 2>&1
    for f in fuzz/out/default/crashes/id:*; do
      [ -e "$f" ] || continue
      out=$(./aba-convert "$f" 2>&1 >/dev/null)
      kind=$(printf "%s" "$out" | grep -oiE "stack-buffer-overflow|signed integer overflow|heap-buffer-overflow" | head -1)
      where=$(printf "%s" "$out" | grep -oE "in [a-zA-Z_]+|aba-convert\.c:[0-9]+" | head -1)
      echo "$(basename "$f")  =>  ${kind:-?}  ${where}"
    done
  '
