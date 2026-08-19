#!/bin/sh
# Repeat the hybrid run and the afl-pair control N times on disjoint CPU cores and
# summarise edge coverage over time and the batches each configuration found that
# reconcile with amounts different from the seed. Each run uses a distinct AFL
# seed, recorded in a manifest. The two configurations are pinned to disjoint
# cores so neither steals the other's CPU; run it in a quiet window so the timing
# is meaningful. Needs payslate-aba-symcc (symcc/build-image.sh); arm64-native.
# The output directory is wiped at the start of every run.
#   Usage: symcc/compare.sh [runs] [seconds]   (default 5 600)
set -eu
cd "$(dirname "$0")/.."
runs="${1:-5}"; secs="${2:-600}"
base=symcc/out/compare
rm -rf "$base" && mkdir -p "$base"
{ echo "# hybrid vs afl-pair comparison"
  echo "runs=$runs seconds=$secs"
  echo "hybrid cores=0,1   afl-pair cores=2,3"
  echo "image: $(docker image inspect payslate-aba-symcc --format '{{.Id}}' 2>/dev/null)"
  echo "uname: $(uname -srm)"
  date -u +"started=%Y-%m-%dT%H:%M:%SZ"
} > "$base/manifest.txt"
i=1
while [ "$i" -le "$runs" ]; do
  seed="$i"; rd="$base/run-$i"; mkdir -p "$rd"
  echo "run $i / $runs (AFL seed $seed, ${secs}s)"
  sh symcc/run-hybrid.sh   "$secs" 0,1 "$rd/hybrid"   "$seed" >"$rd/hybrid.log"   2>&1 &
  ph=$!
  sh symcc/run-afl-pair.sh "$secs" 2,3 "$rd/afl-pair" "$seed" >"$rd/afl-pair.log" 2>&1 &
  pa=$!
  wait "$ph"; wait "$pa"
  echo "run=$i seed=$seed seconds=$secs" >> "$base/manifest.txt"
  i=$((i + 1))
done
date -u +"finished=%Y-%m-%dT%H:%M:%SZ" >> "$base/manifest.txt"
echo "--- summary ---"
python3 symcc/summarise.py "$base"
