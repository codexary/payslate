#!/bin/sh
# Builds the ASan/UBSan converter and replays the robustness-testing regressions.
# After the fixes the converter must process every one without a sanitizer report:
# over-long names are bounded to the output field and out-of-range batch totals are
# rejected in 64-bit arithmetic.
set -eu
cd "$(dirname "$0")/.."
make san CC="${CC:-cc}"
log=tests/.regress.log
: > "$log"
for input in \
  tests/normal.txt \
  fuzz/crashes/name-overflow.txt \
  fuzz/crashes/total-overflow.txt \
  tests/long-name.txt \
  tests/total-overflow.txt
do
  ./aba-convert "$input" >/dev/null 2>>"$log" || true
done
if grep -qiE 'AddressSanitizer|UndefinedBehaviorSanitizer|runtime error' "$log"; then
  echo 'sanitizer finding present:' >&2
  cat "$log" >&2
  exit 1
fi
printf '%s\n' 'ASan/UBSan clean on the robustness-testing regressions'
