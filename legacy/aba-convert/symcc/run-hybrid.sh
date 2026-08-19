#!/bin/sh
# Hybrid run: one AFL++ main instance plus symcc_fuzzing_helper as an AFL
# secondary named "symcc", both over the converter. The helper builds aba-convert
# with SymCC, watches the main instance's queue, solves the branch conditions on
# each new input and offers the solved inputs back as a secondary; AFL adopts the
# ones that reach new edges. Pin the run to two cores with --cpuset-cpus (the
# CPUSET argument) so it is fair against run-afl-pair.sh and reproducible.
# Needs payslate-aba-symcc (symcc/build-image.sh); runs arm64-native.
#   Usage: symcc/run-hybrid.sh [seconds] [cpuset] [outdir] [seed]
#   defaults: 600  0,1  symcc/out/hybrid  1  (seed = AFL -s, record it per run)
set -eu
cd "$(dirname "$0")/.."
secs="${1:-600}"; cpuset="${2:-0,1}"; outdir="${3:-symcc/out/hybrid}"; seed="${4:-1}"
img=payslate-aba-symcc
rm -rf "$outdir" && mkdir -p "$outdir"
docker run --rm --cpuset-cpus "$cpuset" -e SECS="$secs" -e SEED="$seed" \
  -v "$PWD:/src" -v "$PWD/$outdir:/run" -w /run "$img" sh -c '
  cp -r /src/fuzz/seeds seeds; cp /src/fuzz/aba.dict aba.dict
  AFL_LLVM_INSTRUMENT=CLASSIC afl-clang-fast -std=c17 -O1 -g -o aba-afl   /src/aba-convert.c /src/convert.c 2>/dev/null
  symcc                        -std=c17 -O1 -g -o aba-symcc /src/aba-convert.c /src/convert.c 2>/dev/null
  export AFL_SKIP_CPUFREQ=1 AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_NO_AFFINITY=1
  afl-fuzz -s "$SEED" -M main -i seeds -o out -x aba.dict -V "$SECS" -- ./aba-afl @@ >afl.log 2>&1 &
  p=$!; sleep 8
  symcc_fuzzing_helper -o out -a main -n symcc -- ./aba-symcc @@ >helper.log 2>&1 &
  h=$!; wait $p 2>/dev/null || true; kill $h 2>/dev/null || true
'
echo "hybrid run written to $outdir (AFL main + symcc helper), ${secs}s on cores $cpuset, AFL seed $seed"
