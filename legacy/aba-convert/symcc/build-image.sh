#!/bin/sh
# Build the pinned SymCC + AFL++ image used by the hybrid-fuzzing scripts in this
# directory. The build context is just this folder; SymCC and AFL++ are cloned
# inside the image at the pinned commit and tag. Built and run arm64-native.
set -eu
cd "$(dirname "$0")"
docker build -t payslate-aba-symcc -f Dockerfile .
