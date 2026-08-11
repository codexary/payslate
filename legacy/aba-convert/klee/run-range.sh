#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
img=klee/klee:v3.2
rm -rf klee/out-range klee/*.bc
docker run --rm --platform linux/amd64 -v "$PWD:/src" -w /src "$img" sh -c '
  clang -I/src -emit-llvm -c -g -O0 -Xclang -disable-O0-optnone klee/range_check.c -o klee/rc.bc
  klee --output-dir=klee/out-range --libc=uclibc klee/rc.bc
'
