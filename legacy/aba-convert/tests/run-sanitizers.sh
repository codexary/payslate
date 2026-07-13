#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
make san CC="${CC:-cc}"
./aba-convert tests/normal.txt >/dev/null
if ./aba-convert tests/long-name.txt >tests/.long-name.log 2>&1; then
  echo 'expected AddressSanitizer finding for the long account name' >&2
  exit 1
fi
if ! grep -q 'AddressSanitizer: stack-buffer-overflow' tests/.long-name.log; then
  cat tests/.long-name.log >&2
  exit 1
fi
./aba-convert tests/total-overflow.txt >tests/.total-overflow.log 2>&1
if ! grep -q 'runtime error: signed integer overflow' tests/.total-overflow.log; then
  cat tests/.total-overflow.log >&2
  exit 1
fi
printf '%s\n' 'ASan: stack-buffer-overflow reproduced' 'UBSan: signed integer overflow reproduced'
