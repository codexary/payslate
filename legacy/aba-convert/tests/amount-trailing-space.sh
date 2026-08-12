#!/bin/sh
# An amount field is a fixed 10-digit, zero-filled number. KLEE showed that a
# field padded with trailing spaces (digits dropped off the end) is still
# accepted, because the shared fixed-width reader trims trailing spaces before
# the digit check: "00000120  " is read as 120 cents, not the 12000 the operator
# meant, and a control trailer written by the same desktop export reconciles with
# the wrong figure. The reader must reject a space anywhere in the amount field.
# Ordinary (non-sanitizer) build.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
pad40() { printf '%-40s' "$1"; }
expect_reject() {
  set +e; ./aba-convert "$1" >"$tmp/out" 2>"$tmp/err"; got=$?; set -e
  [ "$got" != "0" ] || { echo "$2: accepted an amount with spaces" >&2; exit 1; }
  [ ! -s "$tmp/out" ] || { echo "$2: produced output" >&2; exit 1; }
  grep -qi 'invalid amount' "$tmp/err" || { echo "$2: wrong message ($(cat "$tmp/err"))" >&2; exit 1; }
}
# 1. Trailing spaces drop the last two digits: "00000120  " would read as 120.
{ printf 'H,PAYSLATE\n'
  printf 'D%s123-456000000001%s\n' "$(pad40 'Synthetic One')" '00000120  '
  printf 'T%06d%010d\n' 1 120
} > "$tmp/trailing"
expect_reject "$tmp/trailing" 'amount with trailing spaces'
# 2. An all-space amount (the empty-amount case) is rejected the same way.
{ printf 'H,PAYSLATE\n'
  printf 'D%s123-456000000001%s\n' "$(pad40 'Synthetic One')" '          '
  printf 'T%06d%010d\n' 1 0
} > "$tmp/blank"
expect_reject "$tmp/blank" 'all-space amount'
printf '%s\n' 'amount fields with spaces are rejected'
