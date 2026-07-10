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

Two inherited weaknesses are kept as-is for a robustness review: the account name is copied into a 24-byte
buffer without a length check, and the batch total is accumulated in a 32-bit signed integer. Build the
sanitizer variant and replay the sample inputs to see them:

```sh
sh tests/run-sanitizers.sh
```

`scripts/build-container.sh` and `scripts/build-sanitizers-container.sh` build the same two variants inside a
pinned `gcc:14.2.0` container.
