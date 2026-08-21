English | [简体中文](0017-hybrid-fuzzing.zh-CN.md)

# ADR 0017: Where hybrid fuzzing runs, and when it pays

Ravi Menon — 2026-08-21

## Status

Accepted on 2026-08-21.

## Context

ADR 0016 left one thing open: pairing a solver with the fuzzer to cross the
converter's reconciliation gate. AFL++ alone, plain and with CmpLog, never crossed
it — an arithmetic equality between the declared total and the sum of the amounts
is not something byte mutation solves. SymCC (concolic execution) does solve such
an equality: pointed at a mutated-amount batch it produces a batch whose amounts
differ from the seed yet still reconcile. The question is whether running SymCC
continuously beside the fuzzer earns its keep.

## Decision

Hybrid fuzzing runs locally only, never in CI. It uses SymCC's own
`symcc_fuzzing_helper` as an AFL++ secondary (the documented integration; AFL++'s
`custom_mutators/symcc` is the fallback, not needed here), with SymCC and AFL++
built from the same LLVM 15 in `legacy/aba-convert/symcc/`, built and run
arm64-native.

We use SymCC in two ways, and only one of them is the continuous loop:

- To **generate** a specific input past the gate, SymCC is pointed straight at a
  mutated-amount batch (`symcc/run-symcc.sh symcc/seeds/mismatch.txt`). It solves
  one reliably; the solved batch is kept as a reconciliation regression
  (`tests/reconcile-cases/accept-new-amounts.txt`) and as an AFL++ seed
  (`fuzz/seeds/new-amounts.txt`). This is cheap and worth doing.
- The **continuous** hybrid loop — SymCC beside AFL++ over a time budget — is run
  only when deciding whether the far side of the gate needs more inputs, not
  routinely.

## When it pays

Measured against AFL++ alone on the same machine, the same two CPUs each, the same
seeds and five 10-minute runs (`docs/fuzzing/hybrid-comparison.md`): no edge
difference — both reach 107 edges and hold — and neither produced a reconciling
batch with new amounts within the budget. The reconciled seeds already reach
`write_batch`, so there are no extra edges to win past the gate. The comparison
did not record which inputs the helper received, so it does not say why no new
batch appeared; either way, the continuous loop did not pay here. What pays is the
one-shot generate step above, which produces the diverse reconciling batch without
the loop.

## Alternatives considered

- A nightly CI job for the hybrid loop: rejected. It needs a pinned image, two
  dedicated cores and a time budget, and the comparison shows no coverage or
  reconciliation gain at a 10-minute budget; a longer local run is an experiment,
  not a gate.
- Instrumenting libc with SymCC (a SymCC-compiled musl or uClibc) so the digit
  conversion is traced: considered and rejected as too heavy for the gain. The
  converter was instead changed to read the fixed-width digits without `strtoll`,
  so the arithmetic is in code SymCC already traces; that is a readability change
  recorded with the converter, and it is what lets SymCC see the gate at all.
- CmpLog only: already measured in ADR 0016; it helps with comparisons, not with
  an arithmetic equality.

## A note on the two Z3s

SymCC's QSYM backend solves with the Z3 that the image's Ubuntu ships (`z3`
4.8.12). That is a different thing from the `z3-solver==5.0.0.0` Python package
that proves the withholding schedule (ADR 0016): a different interface, a
different version, a different job. The two version numbers are not an
inconsistency.

## Consequences

The reconciliation gate is covered in three places, each where it belongs: KLEE
explores the reader's paths and its generated cases run in CI (ADR 0016); SymCC
generates a diverse reconciling batch on demand, kept as a regression and a seed;
and AFL++ keeps fuzzing the converter locally (ADR 0014). The continuous hybrid
loop stays available in `legacy/aba-convert/symcc/` for a local experiment but is
not part of CI or the routine fuzzing. ADR 0016's open item — pairing a solver
with the fuzzer — is settled: the pairing works for generating inputs, and the
continuous loop is not worth routine use at these budgets.
