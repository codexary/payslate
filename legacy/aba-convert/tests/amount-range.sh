#!/bin/sh
# A single ten-digit amount at the ceiling is accepted; a signed amount is rejected;
# a batch total above the ten-digit ceiling is rejected. Ordinary (non-sanitizer) build.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
name=$(printf '%-40s' "Synthetic One")
detail() { printf 'D%s123-456000000001%s\n' "$name" "$1"; }
runfile() { set +e; ./aba-convert "$1" >tmp.out 2>tmp.err; printf '%s' "$?"; set -e; }

# 1. single amount 9999999999: accepted, trailer carries it
{ printf 'H,PAYSLATE\n'; detail 9999999999; } > tmp.in
s=$(runfile tmp.in)
[ "$s" = 0 ] || { echo "max single amount rejected (exit $s)" >&2; cat tmp.err >&2; exit 1; }
grep -q '^79999999999' tmp.out || { echo "trailer total wrong:" >&2; cat tmp.out >&2; exit 1; }

# 2. signed amount: rejected as invalid
{ printf 'H,PAYSLATE\n'; detail "-999999999"; } > tmp.in
s=$(runfile tmp.in)
[ "$s" = 2 ] || { echo "signed amount not rejected (exit $s)" >&2; exit 1; }
grep -qi 'invalid amount' tmp.err || { echo "wrong message for signed amount:" >&2; cat tmp.err >&2; exit 1; }

# 3. two amounts over the ten-digit total: rejected
{ printf 'H,PAYSLATE\n'; detail 9999999999; detail 9999999999; } > tmp.in
s=$(runfile tmp.in)
[ "$s" = 2 ] || { echo "over-ceiling total not rejected (exit $s)" >&2; cat tmp.out >&2; exit 1; }
grep -qi 'batch total out of range' tmp.err || { echo "wrong message for total:" >&2; cat tmp.err >&2; exit 1; }

# 4. all-spaces (empty after trimming) amount: rejected as invalid
{ printf 'H,PAYSLATE\n'; detail '          '; } > tmp.in
s=$(runfile tmp.in)
[ "$s" = 2 ] || { echo "empty amount not rejected (exit $s)" >&2; exit 1; }
grep -qi 'invalid amount' tmp.err || { echo "wrong message for empty amount:" >&2; cat tmp.err >&2; exit 1; }

rm -f tmp.in tmp.out tmp.err
printf '%s\n' 'amount range: ceiling accepted, signed rejected, over-total rejected, empty rejected'
