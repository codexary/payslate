English | [简体中文](0007-review-mutation-survivors.zh-CN.md)

# ADR 0007: Review mutation survivors before setting a score gate

Ravi Menon — 2026-05-08

## Status

Accepted on 2026-05-08.

## Context

The YTD half-cent defect used `HALF_UP` where the required rule was `HALF_EVEN`. A test asserting only that the result was nonnegative passed. An exact amount assertion exposed the defect, and the exact `roundedCents` assertion rejects a zero-return mutant that the nonnegative assertion accepts.

The current full `pay-engine` analysis generated 61 mutants: 49 killed, 8 survived and 4 uncovered. The raw score is 49/61 = 80.33% (displayed by PIT as 80%). Line coverage is 81/98 (83% displayed), and test strength is 49/57 (86% displayed). The [docs/quality/mutation-report.md](../quality/mutation-report.md) report lists the current locations and results. Two survivors have domain-specific equivalence proofs in [docs/test-design/equivalent-mutants.md](../test-design/equivalent-mutants.md); excluding only those two gives 49/59 = 83.05%. Six survivors remain unclassified.

The existing JaCoCo 65% bundle branch gate establishes a coverage floor. It does not establish that assertions reject incorrect amounts. Mutation review addresses that separate question.

## Decision

Olivia Brooks and Hannah Schmidt will review each surviving mutant against the business rule and the actual test assertion. A new test must state an independently justified expected result and pass on the original program. When a mutant is equivalent, the review records the code location, operator, input domain and reason. An unclassified or uncovered mutant remains in the denominator.

The pull-request `mutation-changed` job selects changed `pay-engine` production Java paths from the base-to-`HEAD` diff and invokes PIT for each corresponding class-name pattern, including nested classes. With no matching source changes, it makes no PIT invocation. The nightly job runs the full module daily at 03:15 UTC and supports manual dispatch. These scopes are defined in `.github/workflows/ci.yml` and `.github/workflows/mutation-nightly.yml`.

The team reports the raw score, killed/survived/no-coverage counts, and any separately justified adjusted score. It does not impose a numeric mutation threshold on this baseline. A score is a prompt for review, not proof that payroll results are correct.

## Alternatives considered

- **Set a numeric mutation gate immediately:** a single threshold would combine proven equivalents, unclassified survivors and uncovered code without explaining them. Review these categories before selecting a maintainable threshold.
- **Rely only on branch coverage:** execution coverage does not show whether assertions distinguish a wrong amount from the expected amount.
- **Use changed-class analysis alone:** it omits mutations in untouched classes, and a test-only change makes no PIT invocation in this job. Retain the nightly full-module analysis.

## Consequences

Changed-class runs provide focused feedback but cannot assess mutations outside their selected scope. Full runs use more test executions and machine time; the current analysis examined 11 tests and recorded 362 test executions for 61 mutants. These are PIT's analysis counts, not a count of the complete JUnit suite. Both workflows require review of their results.

The withholding proof at line 97 relies on the shipped synthetic example tax schedule and must be revisited when that schedule changes. The overtime-factor proof at line 85 relies on the actual calling domain and observable return value. The six unclassified survivors and four uncovered mutants remain explicit follow-up work; neither passing CI nor the adjusted score resolves them.
