#!/bin/sh
# One batch per exit result the KLEE harness reaches (klee/harness.c), kept as named
# regressions. Each file is asserted by BOTH its exact exit code and rejection
# message AND the reconciliation invariant, so a future change that let a should-be-
# rejected batch through with a self-consistent trailer would still be caught.
#
# These five are selected from the full machine-generated set, one per distinct
# outcome: each rejection message plus one accepted batch. Where a fully printable
# witness exists it is used; otherwise the one with the fewest non-printable bytes.
# The accepted case uses a canonical ten-digit amount. The exhaustive set (one file
# per .ktest, deduplicated by exact bytes) is NOT committed -- see klee/README.md for
# how to regenerate and replay it. Ordinary (non-sanitizer) build.
#
# accept-new-amounts.txt is kept alongside them from a different source: it is the
# byte-for-byte output of a SymCC run (QSYM backend) on the refactored fixed-width
# reader (strtoll removed) from a mutated-amount seed, amounts 12001 and
# 4987530459 reconciling to 4987542460. It guards the reconciliation invariant on
# a non-trivial split, not just the canonical case.
set -eu
cd "$(dirname "$0")/.."
make >/dev/null
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

check() {
  file="tests/reconcile-cases/$1"; want_rc="$2"; want_msg="$3"
  set +e; ./aba-convert "$file" >"$tmp/out" 2>"$tmp/err"; rc=$?; set -e
  [ "$rc" = "$want_rc" ] || { echo "$1: expected rc $want_rc, got $rc ($(cat "$tmp/err"))" >&2; exit 1; }
  if [ -n "$want_msg" ]; then
    grep -qF "$want_msg" "$tmp/err" || { echo "$1: expected message '$want_msg', got '$(cat "$tmp/err")'" >&2; exit 1; }
  fi
  if [ "$rc" = 0 ]; then
    [ -s "$tmp/out" ] || { echo "$1: accepted but produced no output" >&2; exit 1; }
    awk '/^1/{s+=substr($0,length($0)-9,10);c++}/^7/{t=substr($0,2,10)+0;d=substr($0,12,6)+0}
         END{if(s!=t||c!=d){print "sum="s" trailer="t" count="c" declared="d;exit 1}}' "$tmp/out" \
      || { echo "$1: output does not reconcile with its details" >&2; exit 1; }
  else
    [ ! -s "$tmp/out" ] || { echo "$1: rejected (rc=$rc) but left partial output" >&2; exit 1; }
  fi
  printf '  OK %-38s rc=%s%s\n' "$1" "$rc" "${want_msg:+  \"$want_msg\"}"
}

check accept.txt                             0 ""
check accept-new-amounts.txt                 0 ""
check reject-invalid-control-trailer.txt     2 "invalid control trailer"
check reject-does-not-reconcile.txt          2 "control trailer does not reconcile"
check reject-invalid-amount.txt              2 "invalid amount"
check reject-invalid-detail.txt              2 "invalid detail"
printf '%s\n' 'reconcile-cases: five KLEE exit classes plus one SymCC-solved batch checked (exact rc + message + reconciliation invariant)'
