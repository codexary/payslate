English | [简体中文](0001-characterise-legacy-pay-before-rewrite.zh-CN.md)

# ADR 0001: Characterise inherited pay behaviour before rewriting it

## Status

Accepted on 2026-02-25.

## Context

Payslate Online inherits the Payslate 7 `LegacyPayEngine`. It currently supports permanent employment only. It contains payroll rules and dated implementation choices: one long routine, mutable counters, manual loops, an anonymous comparator, `double` intermediates, and sequential tax schedule scanning. The café regression, which keeps the reported shift from Friday 2026-02-13 23:00 to Saturday 2026-02-14 01:00 should pay a permanent employee on $20 per hour $45.00 gross. The inherited code produced $40.00. Existing weekday, weekend, and public holiday tests did not cross midnight within a shift.

## Decision

First express each confirmed pay result as a regression test. For the café case, calculate the expected $20 Friday hour and $25 Saturday hour independently of the engine, and store the case in `docs/test-design/weekend-penalty.md`. Observe the test fail on the inherited implementation, then make the smallest code change that explains the difference: read the current day inside the per-minute loop. Keep the test in CI on pull requests. Do not combine this fix with a broad rewrite of the engine or tax calculation.

## Alternatives considered

- Rewrite the engine immediately. This would change many rules before there are tests that describe the inherited behaviour.
- Change the expected value to $40.00. This would encode the observed underpayment as the requirement.
- Split the customer shift into two shifts before calculation. This could hide the engine's incorrect handling of an otherwise valid cross-midnight shift.

## Consequences

The café regression is repeatable and the code change is narrow. Other payroll cases remain to be characterised; passing this test does not establish the correctness of tax, rounding, overtime, holiday, or all cross-day rules. The legacy style and `double` accumulation remain visible risks until separately tested and changed.
