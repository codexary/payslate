#!/bin/sh
# Control run for the hybrid comparison: one AFL++ main instance plus a second
# AFL++ instance (no SymCC), so both configurations spend the same two cores. Pin
# it with --cpuset-cpus (the CPUSET argument) to two cores disjoint from the
# hybrid run's. Needs payslate-aba-symcc (symcc/build-image.sh); arm64-native.
#   Usage: symcc/run-afl-pair.sh [seconds] [cpuset] [outdir] [seed]
#   defaults: 600  2,3  symcc/out/afl-pair  1  (seed = AFL -s, record it per run)
set -eu
cd "$(dirname "$0")/.."
secs="${1:-600}"; cpuset="${2:-2,3}"; outdir="${3:-symcc/out/afl-pair}"; seed="${4:-1}"
img=payslate-aba-symcc
rm -rf "$outdir" && mkdir -p "$outdir"
docker run --rm --cpuset-cpus "$cpuset" -e SECS="$secs" -e SEED="$seed" \
  -v "$PWD:/src" -v "$PWD/$outdir:/run" -w /run "$img" sh -c '
  cp -r /src/fuzz/seeds seeds; cp /src/fuzz/aba.dict aba.dict
  AFL_LLVM_INSTRUMENT=CLASSIC afl-clang-fast -std=c17 -O1 -g -o aba-afl /src/aba-convert.c /src/convert.c 2>/dev/null
  export AFL_SKIP_CPUFREQ=1 AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_NO_AFFINITY=1
  afl-fuzz -s "$SEED" -M main -i seeds -o out -x aba.dict -V "$SECS" -- ./aba-afl @@ >main.log 2>&1 &
  p=$!; sleep 8
  afl-fuzz -s "$SEED" -S sec -i seeds -o out -x aba.dict -V "$SECS" -- ./aba-afl @@ >sec.log  2>&1 &
  s=$!; wait $p 2>/dev/null || true; kill $s 2>/dev/null || true
'
echo "afl-pair run written to $outdir (AFL main + AFL secondary), ${secs}s on cores $cpuset, AFL seed $seed"
