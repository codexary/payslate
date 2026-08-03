#!/bin/sh
# Coverage-guided AFL++ robustness-testing campaign against the ASan/UBSan build of aba-convert.
# Usage: fuzz/run-afl.sh [seconds]   (default 120). Needs Docker.
set -eu
cd "$(dirname "$0")/.."
seconds="${1:-120}"
img=aflplusplus/aflplusplus:v5.02c
rm -rf fuzz/out && mkdir -p fuzz/out
docker run --rm -v "$PWD:/src" -w /src \
  -e AFL_SKIP_CPUFREQ=1 -e AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 \
  -e ASAN_OPTIONS=abort_on_error=1:symbolize=0:detect_leaks=0 \
  -e UBSAN_OPTIONS=halt_on_error=1:abort_on_error=1 \
  "$img" sh -c "make afl >/dev/null && afl-fuzz -i fuzz/seeds -o fuzz/out -x fuzz/aba.dict -m none -V ${seconds} -- ./aba-convert @@"
