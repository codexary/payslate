# ABA batch converter

A small command-line tool inherited from the Payslate desktop product. It reads a fixed-width batch file and
writes the bank's ABA layout to standard output.

```sh
make
./aba-convert tests/normal.txt
```

The input is a header line beginning `H,PAYSLATE`, then one detail line per payment: a `D`, a 40-character
account name, a 7-character BSB, a 9-character account number and a 10-digit amount in cents. A bad header or a
short detail line exits with status 2.

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
