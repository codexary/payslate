#!/bin/sh
# Replay every KLEE-generated test through a native build of the same harness and
# read_batch, to confirm the reader is memory-safe on the inputs KLEE found and to
# see which lines those inputs never reach. This uses KLEE's own runtest replay:
# the harness is compiled against libkleeRuntest (shipped in the KLEE image) and
# run with KTEST_FILE set, so klee_make_symbolic reads the solved bytes back and
# the harness rebuilds, byte for byte, the batch KLEE explored -- the field layout
# lives only in the harness, never copied into this script. Needs Docker; the
# build is amd64 and uses the image's ASan and gcov. Usage: klee/replay.sh [klee/out]
set -eu
cd "$(dirname "$0")/.."
img=klee/klee:v3.2
out="${1:-klee/out}"
docker run --rm --platform linux/amd64 -v "$PWD:/src" -w /src "$img" sh -c '
  set -eu
  out="'"$out"'"
  rt=/tmp/klee_build160stp_z3/lib
  rm -f *.o *.gcda *.gcno *.gcov klee/replay_bin klee/.replay.err
  gcc -c -I/tmp/klee_src/include -I/src -fsanitize=address --coverage -g -O0 convert.c -o convert.o
  gcc -c -I/tmp/klee_src/include -I/src -fsanitize=address --coverage -g -O0 klee/harness.c -o harness.o
  gcc -fsanitize=address --coverage convert.o harness.o -o klee/replay_bin -L"$rt" -lkleeRuntest -Wl,-rpath,"$rt"
  k=0
  for t in "$out"/*.ktest; do
    KTEST_FILE="$t" ASAN_OPTIONS=detect_leaks=0 ./klee/replay_bin >/dev/null 2>>klee/.replay.err || true
    k=$((k + 1))
  done
  if grep -qiE "AddressSanitizer|runtime error|UndefinedBehavior" klee/.replay.err; then
    echo "sanitizer finding on a KLEE input:" >&2; cat klee/.replay.err >&2; exit 1
  fi
  echo "replayed $k KLEE inputs (of $(ls "$out"/*.ktest | wc -l | tr -d " ") .ktest); ASan clean"
  gcov convert.c >/dev/null 2>&1 || true
  echo "lines in convert.c never reached by these inputs (the harness drives read_batch only):"
  grep -nE "#####" convert.c.gcov || echo "  (none)"
  rm -f *.o *.gcda *.gcno *.gcov klee/replay_bin klee/.replay.err
'
