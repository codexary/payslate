# ABA batch converter

A small command-line tool inherited from the Payslate desktop product. It reads a fixed-width batch file and
writes the bank's ABA layout to standard output.

```sh
make
./aba-convert tests/normal.txt
```

The input is a header line beginning `H,PAYSLATE`, then one detail line per payment: a `D`, a 40-character
account name, a 7-character BSB, a 9-character account number and a 10-digit, zero-filled amount in cents (digits only; a space anywhere in the field is rejected). The batch ends
with a control trailer line: a `T`, a 6-digit record count and a 10-digit total in cents, then nothing but the
line ending. The converter reads the whole input first and writes nothing unless the header, the record count
and the total all reconcile; a bad header, a short detail line, a missing or non-reconciling trailer, trailing
characters on the trailer, an empty batch, or data after the trailer exits with status 2. An export without a
trailer (an older desktop build) is rejected — re-export from a patched Payslate 7.

Two inherited weaknesses were found with a robustness review and fixed: the account name was copied into a
24-byte buffer without a length check, and the batch total was accumulated in a 32-bit signed integer. The
copy is now bounded to the 24-character output field, and the total is accumulated and range-checked in
64-bit arithmetic. The minimised crashes are kept as sanitizer regressions, which must stay clean:

```sh
sh tests/run-sanitizers.sh
```

Coverage-guided robustness testing (fuzzing) with AFL++ drives the ASan/UBSan build. Run a local campaign in a
terminal to watch the status screen, then triage any saved crashes; minimise a new crash with `afl-tmin` and
add it under `fuzz/crashes/` as a sanitizer regression:

```sh
sh fuzz/run-afl.sh 120
sh fuzz/triage.sh
```

`scripts/build-container.sh` and `scripts/build-sanitizers-container.sh` build the same two variants inside a
pinned `gcc:14.2.0` container.

`klee/` holds a KLEE harness for the batch reader (`read_batch` in `convert.c`). It runs in the official `klee/klee:v3.2` image, which is amd64 only and runs under emulation on Apple silicon:

```sh
sh klee/run-klee.sh 1   # explore a one-record batch; tests go to klee/out
sh klee/run-range.sh    # the single-amount range check
sh klee/replay.sh       # replay the generated tests natively under ASan, with gcov
```

`symcc/` holds a pinned image for the hybrid-fuzzing lab: SymCC with the QSYM backend and AFL++ `v5.02c`, both built from LLVM 15, so the converter is instrumented by the same compiler the fuzzer is built against. Unlike the KLEE image it is built and run arm64-native. Build it once with:

```sh
sh symcc/build-image.sh   # tags the image payslate-aba-symcc
```

Run SymCC on the converter from a seed and classify what it solves:

```sh
sh symcc/run-symcc.sh                             # from a mutated-amount batch (the default)
sh symcc/run-symcc.sh fuzz/seeds/two-records.txt  # from a batch that already reconciles
```

Compare hybrid fuzzing (AFL++ with SymCC beside it) against AFL++ alone over repeated runs on disjoint CPU cores, in a quiet window:

```sh
sh symcc/compare.sh 5 600   # 5 runs of 10 minutes each; a summary prints at the end
```

`compare.sh` runs `run-hybrid.sh` and `run-afl-pair.sh` for each run, then `summarise.py`, which reads every run's `plot_data` and replays the queues to count batches that reconcile with amounts different from the seed.
