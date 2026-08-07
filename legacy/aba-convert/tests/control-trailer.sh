#!/bin/sh
# The control trailer must be exactly T + 6 digits + 10 digits with only a line
# ending after it (\n or \r\n); trailing characters, an empty batch, a wrong
# header and data after the trailer are rejected. Ordinary (non-sanitizer) build.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
pad40() { printf '%-40s' "$1"; }
detail() { printf 'D%s123-456000000001%010d\n' "$(pad40 'Synthetic One')" "$1"; }
expect() {
  set +e; ./aba-convert "$1" >"$tmp/out" 2>"$tmp/err"; got=$?; set -e
  [ "$got" = "$2" ] || { echo "$3: expected rc $2, got $got ($(cat "$tmp/err"))" >&2; exit 1; }
}
{ printf 'H,PAYSLATE\n'; detail 12000; printf 'T%06d%010d\n' 1 12000; } > "$tmp/ok"
expect "$tmp/ok" 0 'valid batch'
grep -q '^70000012000000001' "$tmp/out" || { echo 'valid batch output trailer wrong' >&2; exit 1; }
{ printf 'H,PAYSLATE\n'; detail 12000; printf 'T%06d%010dxyz\n' 1 12000; } > "$tmp/garbage"
expect "$tmp/garbage" 2 'trailer with trailing characters'
grep -qi 'invalid control trailer' "$tmp/err" || { echo 'wrong message for trailing chars' >&2; exit 1; }
{ printf 'H,PAYSLATE\n'; printf 'T%06d%010d\n' 0 0; } > "$tmp/empty"
expect "$tmp/empty" 2 'empty batch'
grep -qi 'empty batch' "$tmp/err" || { echo 'wrong message for empty batch' >&2; exit 1; }
{ printf 'H,OTHER\n'; detail 12000; printf 'T%06d%010d\n' 1 12000; } > "$tmp/header"
expect "$tmp/header" 2 'wrong header'
grep -qi 'invalid header' "$tmp/err" || { echo 'wrong message for a wrong header' >&2; exit 1; }
[ ! -s "$tmp/out" ] || { echo 'wrong header produced output' >&2; exit 1; }
{ printf 'H,PAYSLATE\n'; detail 12000; printf 'T%06d%010d\n' 1 12000; detail 34050; } > "$tmp/after"
expect "$tmp/after" 2 'data after the trailer'
grep -qi 'data after control trailer' "$tmp/err" || { echo 'wrong message for data after the trailer' >&2; exit 1; }
[ ! -s "$tmp/out" ] || { echo 'data after the trailer produced output' >&2; exit 1; }
./aba-convert "$tmp/ok" > "$tmp/ok.out"
{ printf 'H,PAYSLATE\n'; detail 12000; printf 'T%06d%010d\r\n' 1 12000; } > "$tmp/crlf"
expect "$tmp/crlf" 0 'trailer ending in CR LF'
cmp -s "$tmp/out" "$tmp/ok.out" || { echo 'CR LF trailer changed the output' >&2; exit 1; }
printf '%s\n' 'control trailer: exact format required; CR LF accepted; empty batch, wrong header and data after the trailer rejected'
