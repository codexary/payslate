# Pinned image for the hybrid-fuzzing lab: SymCC (QSYM backend) and AFL++ built
# from the same LLVM 15, so "AFL++ alone" and "AFL++ next to SymCC" share one
# AFL++. SymCC's README lists LLVM 8 to 18 and its CMake warns outside 8-17;
# its official image uses LLVM 15, so the converter here is instrumented with
# clang-15, the same compiler AFL++ is built against. The base is pinned by its
# multi-arch index digest; this lab is
# built and run arm64-native on Apple silicon (amd64 digest
# sha256:0199853f6d6b20b0424f3c5694a72a62764f01e6a771b1eb48a4197848986c7e,
# arm64 digest sha256:a8cdd2158a73d7e5c02aa351fe269f48f57cf710a241db86e9ede371fc150149),
# unlike the KLEE image, which is amd64-only and runs under emulation.
#
# The QSYM backend is required. The simple backend prints a solved input to
# stderr but writes no test case to SYMCC_OUTPUT_DIR, so it cannot feed a fuzzer.
FROM ubuntu:jammy-20260731.1@sha256:3b06811b2afd352be909dd088a004166d665dc76d38b13eada33522a9d915c6f

ENV DEBIAN_FRONTEND=noninteractive
ENV RUSTUP_HOME=/usr/local/rustup CARGO_HOME=/usr/local/cargo
ENV PATH=/usr/local/cargo/bin:/symcc/build:/aflpp:$PATH

RUN apt-get update && apt-get install -y \
      clang-15 llvm-15-dev libz3-dev z3 \
      cmake ninja-build g++ git python3-pip zlib1g-dev curl ca-certificates \
    && pip3 install lit && rm -rf /var/lib/apt/lists/*

# Rust is pinned to the version SymCC's official image uses to build the helper.
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
      | sh -s -- -y --default-toolchain 1.94.0 --profile minimal \
    && chmod -R a+w "$RUSTUP_HOME" "$CARGO_HOME"

# SymCC pinned to a specific upstream commit for a reproducible build; this
# commit's CMake accepts LLVM 15.
ARG SYMCC_COMMIT=3b8acabf06c8
RUN git clone https://github.com/eurecom-s3/symcc.git /symcc \
    && cd /symcc && git checkout ${SYMCC_COMMIT} \
    && git submodule update --init --recursive
RUN mkdir /symcc/build && cd /symcc/build \
    && cmake -G Ninja -DSYMCC_RT_BACKEND=qsym \
        -DCMAKE_BUILD_TYPE=RelWithDebInfo \
        -DLLVM_DIR=/usr/lib/llvm-15/cmake -DZ3_TRUST_SYSTEM_VERSION=on /symcc \
    && ninja
RUN cargo install --locked --path /symcc/util/symcc_fuzzing_helper --root /usr/local
ENV SYMCC_REGULAR_LIBCXX=1

# AFL++ pinned to the same tag the team already uses to fuzz the converter
# (fuzz/run-afl.sh), here built against LLVM 15 to match the instrumented target.
ARG AFLPP_TAG=v5.02c
RUN git clone --depth 1 -b ${AFLPP_TAG} \
      https://github.com/AFLplusplus/AFLplusplus.git /aflpp \
    && cd /aflpp && LLVM_CONFIG=llvm-config-15 CC=clang-15 CXX=clang++-15 make source-only
ENV AFL_PATH=/aflpp

WORKDIR /work
