English | [简体中文](equivalent-mutants.zh-CN.md)

# Equivalent mutants

Olivia Brooks — 2026-05-08

`SURVIVED` means the current tests did not reject a mutant; it does not prove equivalence. The full `pay-engine` analysis with PIT 1.30.1 and `pitest-junit5-plugin` 1.2.3 has eight survivors. Only the following two have equivalence proofs. Both are `ConditionalsBoundaryMutator` changes in `pay-engine/src/main/java/com/elsworne/payslate/engine/LegacyPayEngine.java`.

## Overtime factor: line 85

In `overtimeFactor`, `factor < tier` becomes `factor <= tier`. The predicates differ only when `factor == tier`. The original leaves `factor` unchanged; the mutant assigns the equal value `tier`. Off equality, both execute the same branch and return the same value.

The domain is the method's actual use from `calculate`: finite positive factors `1.0`, `1.25`, `1.5` and `2.25`, with overtime tiers `1.5` and `2.0`. The observable result is the returned multiplier and the pay calculated from it. At equality both values are the same positive `double`, so the assignment changes neither. This proof does not require assumptions about arbitrary floating-point inputs outside these calls. Recheck it if the calling domain or the method's observable behavior changes.

## Withholding: line 97

In `withholding`, `gross.compareTo(candidate) >= 0` becomes `> 0`. For a non-null `BigDecimal` gross amount and the shipped `example-tax-schedule.csv`, the row selection can differ only when gross equals a schedule minimum. The schedule has minima `0.00`, `500.00` and `1000.00`, with base/rate pairs `0.00/0.00`, `0.00/0.10` and `50.00/0.20`.

The returned amount is `base + (gross - min) * rate`, rounded to two decimal places with `HALF_UP`.

| Gross at the minimum | Original: select the row at equality | Mutant: retain the preceding row or initial values | Returned amount in both |
| ---: | --- | --- | ---: |
| 0.00 | `0 + (0 - 0) * 0` | Initial `min`, `base` and `rate` are all zero | 0.00 |
| 500.00 | `0 + (500 - 500) * 0.10` | `0 + (500 - 0) * 0` | 0.00 |
| 1000.00 | `50 + (1000 - 1000) * 0.20` | `0 + (1000 - 500) * 0.10` | 50.00 |

At every other gross amount the predicates select the same rows; below zero both retain the initial zero values. At each minimum the formulas agree before rounding, and both versions return the same two-decimal value. The changed condition does not alter resource handling or CSV parsing. This is a proof for the shipped synthetic example schedule, which is not an official tax table. Recheck the proof whenever the schedule changes; it does not establish equivalence for an arbitrary production schedule.

## Items still requiring review

The other six survivors remain unclassified: four conditional boundaries in `calculate` at lines 17, 36 (two) and 38; removal of `Collections.sort` at line 24; and the anonymous comparator's zero return at line 25. They require targeted tests or further analysis. Four additional mutants in `PayCli.main` have `NO_COVERAGE`, at lines 17, 23 (two) and 25 of `PayCli.java`; none is classified as equivalent. The [mutation report](../quality/mutation-report.md) lists all eight survivors and the analysis totals.

## Why the exact cents assertion matters

`roundedTotalIsNotNegative` cannot distinguish the incorrect half-up result `0.01` from the required half-even result `0.00` for an addition of `0.005` to a new accumulator. Its cents assertion also accepts zero. `roundsHalfCentToEvenAndReportsExactCents` first expects `0.00`, exposing the former `HALF_UP` defect. With the `HALF_EVEN` fix, it then adds `12.34` to the same accumulator, expects `12.34`, and checks exactly `1234` cents. In the current analysis, this test kills the `PrimitiveReturnsMutator` change that returns zero from `roundedCents` at line 12 of `YtdAccumulator.java`. A non-null assertion would likewise reject null while allowing incorrect non-null amounts.

The raw score is 49/61 = 80.33%. Excluding only the two proven equivalents gives 49/59 = 83.05%. The six unclassified survivors and four uncovered mutants stay in the adjusted denominator. These scores describe assertion sensitivity to the selected mutations, not payroll correctness.
