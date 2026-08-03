#!/bin/sh
# A 24-character account name is kept in full; a 25-character name is truncated to
# the 24-character output field. Uses the ordinary (non-sanitizer) build.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
pad40() { printf '%-40s' "$1"; }
line() { printf 'D%s123-4560000000010000012000\n' "$(pad40 "$1")"; }
name24=$(printf 'X%.0s' $(seq 1 24))
name25=$(printf 'X%.0s' $(seq 1 25))
want24=$(printf 'X%.0s' $(seq 1 24))
# 24-char name: kept in full (account_name column holds all 24)
out24=$(printf 'H,PAYSLATE\n%s' "$(line "$name24")" | { cat > tmp.in; ./aba-convert tmp.in; })
title24=$(printf '%s\n' "$out24" | sed -n '2p' | cut -c18-41)
# 25-char name: truncated to 24
out25=$(printf 'H,PAYSLATE\n%s' "$(line "$name25")" | { cat > tmp.in; ./aba-convert tmp.in; })
title25=$(printf '%s\n' "$out25" | sed -n '2p' | cut -c18-41)
rm -f tmp.in
fail=0
[ "$title24" = "$want24" ] || { echo "24-char name not kept: [$title24]" >&2; fail=1; }
[ "$title25" = "$want24" ] || { echo "25-char name not truncated to 24: [$title25]" >&2; fail=1; }
[ "$fail" = 0 ] || exit 1
printf '%s\n' 'account name: 24 chars kept, 25 chars truncated to 24'
