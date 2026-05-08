English | [简体中文](mutation-report.zh-CN.md)

# Mutation report

Olivia Brooks — 2026-05-08

## Scope and results

Full `pay-engine` analysis uses PIT 1.30.1, `pitest-junit5-plugin` 1.2.3 and the default mutators. The current code rounds the YTD total with `HALF_EVEN`. `LegacyPayEngine.calculate` checks every shift for null values, missing endpoints and an end not after its start before sorting; the duration limits are checked afterward in the calculation loop.

| Measure | Result |
| --- | ---: |
| Line coverage for mutated classes | 81/98 (83% displayed) |
| Generated mutants | 61 |
| Killed | 49 |
| Survived | 8 |
| No coverage | 4 |
| Raw mutation score | 49/61 = 80.33% (80% displayed) |
| Test strength on covered mutants | 49/57 = 85.96% (86% displayed) |
| Tests PIT examined / test executions | 11 / 362 |
| Proven equivalent survivors | 2 |
| Adjusted score, excluding only proven equivalents | 49/59 = 83.05% |

The line coverage denominator is PIT's scope of mutated classes, not the JaCoCo bundle denominator. Test strength excludes the four uncovered mutants. The two proven equivalents remain `SURVIVED` in PIT; only the separately stated adjusted score excludes them. Six unclassified survivors and all four uncovered mutants remain in both score denominators. The test counts describe this analysis, not the size of the complete JUnit suite.

## Surviving mutants

All eight survivors are in `pay-engine/src/main/java/com/elsworne/payslate/engine/LegacyPayEngine.java`. Line numbers refer to the current source. Operator names below are PIT class names without their package prefix.

| Method | Line | Operator | Change | Review classification |
| --- | ---: | --- | --- | --- |
| `calculate` | 17 | `ConditionalsBoundaryMutator` | `hourlyRate.signum() < 0` → `<= 0` | Unclassified |
| `calculate` | 24 | `VoidMethodCallMutator` | Remove `Collections.sort` | Unclassified |
| Anonymous comparator `compare` | 25 | `PrimitiveReturnsMutator` | Return `0` instead of comparing start times | Unclassified |
| `calculate` | 36 | `ConditionalsBoundaryMutator` | `minutes > 24*60` → `>= 24*60` | Unclassified |
| `calculate` | 36 | `ConditionalsBoundaryMutator` | `minutes < 1` → `<= 1` | Unclassified |
| `calculate` | 38 | `ConditionalsBoundaryMutator` | `paidMinutes < 180` → `<= 180` | Unclassified |
| `overtimeFactor` | 85 | `ConditionalsBoundaryMutator` | `factor < tier` → `<= tier` | Proven equivalent for the stated domain |
| `withholding` | 97 | `ConditionalsBoundaryMutator` | `gross.compareTo(candidate) >= 0` → `> 0` | Proven equivalent with the shipped example schedule |

The [equivalence note](../test-design/equivalent-mutants.md) gives the two proofs and their limits. Survival alone establishes neither equivalence nor a production defect. Review the remaining six against the input contract and expected results, including whether reordered shifts can change pay and whether boundary inputs expose an observable difference.

The four `NO_COVERAGE` mutants are negated conditions in `PayCli.main`, in `PayCli.java` at lines 17, 23 (two mutants) and 25. They have no equivalence classification.

## Rounding assertions

`roundedTotalIsNotNegative` accepts both `0.01` from half-up rounding of `0.005` and the required half-even result `0.00`. Its nonnegative cents assertion also accepts a return value of zero. `roundsHalfCentToEvenAndReportsExactCents` requires `0.00`, then `12.34` after the next addition, and exactly `1234` cents. It exposed the rounding defect before the `HALF_EVEN` fix and now kills the zero-return mutant in `YtdAccumulator.roundedCents` at line 12. Both generated `YtdAccumulator` mutants are killed.

## CI configuration and review

In `.github/workflows/ci.yml`, the `mutation-changed` job runs on pull requests. It compares Java source paths under `pay-engine/src/main/java` between the pull-request base and `HEAD`, converts each changed path into a class name, and invokes PIT separately with `-DtargetClasses=<class-name>*`. The wildcard includes nested classes. If there are no matching Java paths, the loop makes no PIT invocation. A test-only change therefore does not trigger mutation analysis in this job.

In `.github/workflows/mutation-nightly.yml`, the full-module job is scheduled daily at 03:15 UTC (`15 3 * * *`) and also supports manual dispatch. It runs:

```sh
./mvnw -B -ntp -pl pay-engine test-compile org.pitest:pitest-maven:mutationCoverage
```

Changed-class analysis provides focused feedback; the nightly configuration also includes production classes untouched by a pull request. Neither scope proves that all interactions are correct. There is no numeric mutation threshold. Reviewers inspect the surviving and uncovered mutants and justify any exclusion from the adjusted score.
