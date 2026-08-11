#!/bin/sh
# Build read_batch and the harness to LLVM bitcode and run KLEE over a batch whose
# amounts and control trailer are symbolic. Needs Docker; the KLEE image is amd64
# only, so it runs under emulation on Apple-silicon machines. NREC selects the
# record count (default 2).
set -eu
cd "$(dirname "$0")/.."
img=klee/klee:v3.2
nrec="${1:-2}"
rm -rf klee/out klee/*.bc
docker run --rm --platform linux/amd64 -v "$PWD:/src" -w /src "$img" sh -c "
  clang -I/src -emit-llvm -c -g -O0 -Xclang -disable-O0-optnone -DNREC=${nrec} klee/harness.c -o klee/harness.bc
  clang -I/src -emit-llvm -c -g -O0 -Xclang -disable-O0-optnone convert.c -o klee/convert.bc
  llvm-link klee/harness.bc klee/convert.bc -o klee/combined.bc
  klee --output-dir=klee/out --libc=uclibc klee/combined.bc
"
