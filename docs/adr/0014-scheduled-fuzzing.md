English | [简体中文](0014-scheduled-fuzzing.zh-CN.md)

# ADR 0014: Scheduled fuzzing

Ravi Menon — 2026-08-05

## Status

Accepted on 2026-08-05.

## Context

Before the external security assessment, the team added coverage-guided robustness tests: Jazzer for the ABA parser and the timesheet importer, and AFL++ for the C converter `aba-convert`. The repository is on the free GitHub plan with a monthly Actions-minute budget, so scheduled runs must be bounded.

## Decision

On every pull request, run a short Jazzer pass over `AbaReferenceFuzzTest` and `TimesheetImporterFuzzTest` in the `fuzz-smoke` job in [ci.yml](../../.github/workflows/ci.yml). Jazzer fuzzes only one `@FuzzTest` per JVM invocation and skips the others, so invoke each target separately with its own `-Dtest`. Set `-Djazzer.max_duration=60s` for each target to keep the two together near two minutes of wall-clock time. Jazzer 0.30.0 ignores `-Djazzer.max_total_time`; do not use that property to bound a run.

Use [fuzz-nightly.yml](../../.github/workflows/fuzz-nightly.yml) to fuzz the same two targets for longer, still in separate invocations, with `-Djazzer.max_duration=5m` each so the two together stay within ten minutes. Skip scheduled fuzzing when no commit landed in the last day. Allow manual runs through `workflow_dispatch` even when there are no recent commits, and use `concurrency` with `cancel-in-progress: true` to cancel an in-progress run for the same ref.

Keep AFL++ on `aba-convert` out of CI. It needs the pinned sanitizer image `aflplusplus/aflplusplus:v5.02c` and runs at a few hundred executions per second, so a per-PR or nightly run yields little value. Run it locally when the converter changes, then triage and minimise each crash and commit it as a sanitizer regression in [fuzz/crashes/](../../legacy/aba-convert/fuzz/crashes/), replayed by [tests/run-sanitizers.sh](../../legacy/aba-convert/tests/run-sanitizers.sh).

Run the overlap-finder property as a deterministic regression inside `verify`, not as a scheduled fuzz target.

## Alternatives considered

- Bound runs with `-Djazzer.max_total_time`: ineffective because Jazzer 0.30.0 ignores it; the run would use the duration in the harness annotation instead.
- List all targets in one invocation: rejected because Jazzer fuzzes only one `@FuzzTest` per JVM invocation and skips all the others.
- Add AFL++ to the nightly workflow: rejected because the sanitizer Docker image requirement and low execution rate give little value per scheduled run.
- Run a single long fuzzing session on every pull request: rejected because it slows every PR while most do not touch a parser.

## Consequences

A short fuzz pass that finds nothing means only that no finding was reached within that time budget; it does not establish that the parser is safe. Reproduce and minimise every saved crash, and turn it into a deterministic regression before fixing the defect.

Keep the separate target calls when editing the workflows: Jazzer's one-target-per-invocation behaviour means that combining them would silently leave other targets unfuzzed.
