#!/bin/sh
# A desktop export truncated at a line boundary must be rejected, not silently
# converted with a record missing; a mid-line truncation must leave no partial
# ABA output. Uses the ordinary (non-sanitizer) build.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
pad40() { printf '%-40s' "$1"; }
{
  printf 'H,PAYSLATE\n'
  printf 'D%s123-456000000001%010d\n' "$(pad40 'Synthetic One')" 12000
  printf 'D%s123-456000000002%010d\n' "$(pad40 'Synthetic Two')" 34050
} > "$tmp/full.txt"

# 1. Truncated at a line boundary: the second record is gone.
head -n 2 "$tmp/full.txt" > "$tmp/line.txt"
if ./aba-convert "$tmp/line.txt" > "$tmp/out1" 2>/dev/null; then
  echo 'line-boundary truncation was accepted (silent short pay)' >&2
  exit 1
fi
if [ -s "$tmp/out1" ]; then
  echo 'line-boundary truncation produced output' >&2
  exit 1
fi

# 2. Truncated in the middle of a record.
size=$(wc -c < "$tmp/full.txt")
head -c "$((size - 20))" "$tmp/full.txt" > "$tmp/mid.txt"
./aba-convert "$tmp/mid.txt" > "$tmp/out2" 2>/dev/null || true
if [ -s "$tmp/out2" ]; then
  echo 'mid-line truncation left partial output' >&2
  exit 1
fi

printf '%s\n' 'truncated exports are rejected without output'
