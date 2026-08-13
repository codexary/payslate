English | [简体中文](0016-symbolic-execution-and-solver-checks.zh-CN.md)

# ADR 0016: Where symbolic execution and the solver checks run

Ravi Menon — 2026-08-13

## Status

Accepted on 2026-08-13.

## Context

Two tools joined the batch reader and the pay engine this week. KLEE runs the extracted batch reader symbolically — the structure fixed, the amount and control-trailer digits symbolic — and explores every path through parsing and reconciliation within those bounds. Z3 proves properties of the withholding schedule for every whole-cent gross. Both are useful, and both cost something to run; we need to record where each belongs so later changes keep them in the right place.

## Decision

KLEE runs locally, not in continuous integration, and is run when the reader or the reconciliation code changes. Its image is published for amd64 only, so it runs under emulation on Apple-silicon machines; it needs the code built to LLVM bitcode; and its path count grows with the record count. Before this week's fix the extracted reader roughly tripled per record, because the amount field was space-trimmed and so had three accepted readings; requiring the amount to be exactly ten digits (see `legacy/aba-convert/tests/amount-trailing-space.sh`) made the growth linear. The whole converter on an unconstrained input file still explodes: twenty bytes already give 1102 completed paths. None of that belongs in a per-change gate. What does run in CI is the set of reconciliation cases KLEE generated: they are kept as regressions and replayed by the converter job. The replay builds the harness and reader natively with gcc, because the clang in the KLEE image ships without an AddressSanitizer runtime; gcc provides both AddressSanitizer and gcov line coverage. `legacy/aba-convert/klee/README.md` records how to run KLEE and regenerate the cases.

The Z3 checks run in CI. A dedicated workflow runs `analysis/tax_schedule_check.py` when the schedule CSV or the `analysis/` directory changes, with least privilege and a short timeout. It runs on pull requests and on manual dispatch, not on a push to the main branch; a pull request runs it before the change can merge, so a new schedule is always checked first. The solver is pinned to `z3-solver==5.0.0.0`; if a later interface change broke it the fallback was `4.16.0.0`, but 5.0.0.0 ran as written and no fallback was needed.

## Alternatives considered

- KLEE in a nightly job: rejected for now. Under emulation, and with the path count growing per record, a nightly run would be slow and noisy, and the regression cases it already produced run in CI and catch the behaviour it found. This can be revisited if the reader grows.
- AFL++ with CmpLog in CI: considered and measured on the new converter, 120 seconds each. Neither plain AFL++ nor AFL++ with CmpLog crossed the arithmetic reconciliation gate: every accepted input in the saved queues kept the seed amounts, and none carried amounts that differ from the seeds yet still sum to the declared total. CmpLog helps with comparisons, not with an arithmetic equality, so pairing a solver with the fuzzer is a separate step, considered later. AFL++ stays out of CI.
- Symbolic execution of the whole converter: rejected. Given an unconstrained input file it does not finish on realistic sizes; the extracted reader is the tractable unit, and the reconciliation gate is exactly what it exercises.

## Consequences

KLEE stays a local tool aimed at the reader; a change to the reader or the reconciliation still calls for a local KLEE run, and the generated cases guard against regressions in CI. The schedule proof guards the withholding schedule in CI, so a change to the CSV or the engine's rounding fails there before it reaches the engine tests. Because KLEE is not a gate, a reader change that no one runs it against is only caught by the regression cases, not by fresh exploration; the README makes the local step explicit. Pairing the fuzzer with a solver to cross the reconciliation gate remains open.
