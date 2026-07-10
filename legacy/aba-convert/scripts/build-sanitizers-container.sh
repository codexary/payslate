#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker run --rm --platform linux/amd64 -v "$PWD:/src" -w /src gcc:14.2.0 make san
