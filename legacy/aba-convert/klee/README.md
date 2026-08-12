English | [简体中文](README.zh-CN.md)

# Symbolic execution of the batch reader

`harness.c` makes the amount and control-trailer low digits of a small batch
symbolic and calls `read_batch` (the structure is concrete, so the paths stay
bounded). `klee_assert` checks that an accepted batch reconciles -- its record
count and total equal what the control trailer declares. `range_check.c` is a
standalone harness that runs ten symbolic digits through the single-amount range
check and shows the overflow branch is unreachable.

Everything runs in the official KLEE image (`klee/klee:v3.2`), which is amd64
only, so on Apple-silicon machines it runs under emulation. Build commands are
fixed in the scripts; no times are reported.

## Scripts

- `run-klee.sh [N]` -- run KLEE over a batch of `N` records (default 2); writes
  tests to `klee/out`.
- `run-range.sh` -- run the range-check harness; writes to `klee/out-range`.
- `replay.sh [klee/out]` -- replay every generated `.ktest` through a native build
  of the same harness and `read_batch` using KLEE's runtest library
  (`libkleeRuntest` + `KTEST_FILE`), so `klee_make_symbolic` reads the solved bytes
  back and the harness reconstructs, byte for byte, the batch KLEE explored. The
  build is done with gcc, because the KLEE image's clang has no AddressSanitizer
  runtime; gcc provides both ASan and `--coverage`. The run reports any sanitizer
  finding and, via gcov, the lines no input reaches (the "amount out of range"
  branch among them).

## Regenerating the reconciliation regression cases

`tests/reconcile-cases/` keeps one readable batch per exit result, selected from
the full set KLEE generates. To regenerate the full set (one batch per `.ktest`,
deduplicated by exact bytes) and inspect it:

```sh
sh klee/run-klee.sh 1                 # 151 tests at one record, in klee/out
rt=/tmp/klee_build160stp_z3/lib       # inside the KLEE image
gcc -DDUMP_BATCH -I/tmp/klee_src/include -I. klee/harness.c -o /tmp/dump \
    -L"$rt" -lkleeRuntest -Wl,-rpath,"$rt"
for t in klee/out/*.ktest; do KTEST_FILE="$t" BATCH_OUT="/tmp/$(basename "$t" .ktest).bin" /tmp/dump; done
```

`DUMP_BATCH` makes the harness write the reconstructed batch (to `$BATCH_OUT`)
instead of parsing it. The committed cases are the distinct outcomes picked from
that set: one per rejection message plus one accepted batch, preferring a fully
printable witness and, for the accepted case, a canonical ten-digit amount.
`tests/run-reconcile-cases.sh` asserts each one's exact exit code and message and
the reconciliation invariant.
