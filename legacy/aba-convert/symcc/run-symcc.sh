#!/bin/sh
# Run SymCC (QSYM backend) on aba-convert from a seed and classify what it solves.
# SymCC makes the seed's bytes symbolic, follows the one path the seed drives, and
# at each branch on that path tries to solve for an input that flips it. A batch
# that already reconciles only lets it flip the gate so the batch STOPS
# reconciling; a mutated-amount batch that fails the gate (the kind a fuzzer
# produces) lets it try to solve the gate the other way. Each generated input is
# replayed under a plain build and classified by exit status and by whether its
# amounts differ from the seed's. Needs the payslate-aba-symcc image
# (symcc/build-image.sh); runs arm64-native. Output goes to symcc/out (ignored).
#   Usage: symcc/run-symcc.sh [seed]   (default symcc/seeds/mismatch.txt)
set -eu
cd "$(dirname "$0")/.."
seed="${1:-symcc/seeds/mismatch.txt}"
img=payslate-aba-symcc
rm -rf symcc/out && mkdir -p symcc/out/cases
docker run --rm -e SEED="$seed" -v "$PWD:/src" -w /src "$img" sh -c '
  cc    -std=c17 -O1 -g -o symcc/out/aba-plain aba-convert.c convert.c
  symcc -std=c17 -O1 -g -o symcc/out/aba-symcc aba-convert.c convert.c 2>/dev/null
  seedamts=$(awk "/^D/ { printf \"%s,\", substr(\$0, 58, 10) }" "$SEED")
  SYMCC_INPUT_FILE="$SEED" SYMCC_OUTPUT_DIR=symcc/out/cases \
    symcc/out/aba-symcc "$SEED" >/dev/null 2>&1 || true
  gen=$(ls symcc/out/cases 2>/dev/null | wc -l | tr -d " ")
  echo "seed: $SEED (amounts $seedamts)"
  echo "generated inputs: $gen"
  acc=0; accnew=0
  for f in symcc/out/cases/*; do
    [ -e "$f" ] || continue
    if symcc/out/aba-plain "$f" > symcc/out/replay 2>/dev/null; then
      acc=$((acc + 1))
      amts=$(awk "/^1/ { printf \"%s,\", substr(\$0, length(\$0) - 9) }" symcc/out/replay)
      if [ "$amts" != "$seedamts" ]; then
        accnew=$((accnew + 1))
        echo "  accepted, reconciles with amounts different from the seed: $amts"
      fi
    fi
  done
  echo "accepted (exit 0): $acc"
  echo "accepted that reconcile with amounts different from the seed: $accnew"
'
