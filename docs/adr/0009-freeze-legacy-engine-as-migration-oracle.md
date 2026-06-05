English | [简体中文](0009-freeze-legacy-engine-as-migration-oracle.zh-CN.md)

# ADR 0009: Freeze the legacy pay engine as a migration oracle

Ravi Menon — 2026-06-05

## Status

Accepted on 2026-06-05.

## Context

The Payslate Online team is refactoring the inherited Payslate 7 calculation path. The old engine has many observable payroll rules, while no one has written an expected result for every possible employee and timesheet. A refactor can preserve unit-test examples yet change a weekend rate, rounding point, tax amount, retirement contribution or leave accrual. Margaret O'Connell can validate specific business-rule examples, but cannot manually calculate every generated case.

`legacy-engine` is the frozen Payslate 7 calculator; its source differs only in package declaration. `PayEnginePropertiesTest.parallelRunOracle` constructs equivalent inputs for both engines and compares gross, tax, retirement and leave over 100 generated cases. The test supplies zero deductions because the old interface has no deduction input. The generated cases are single shifts of 1 to 600 minutes starting at 08:00, with integer hourly rates from 1 to 500, permanent or casual employment, holiday flags and days from 22 to 28 February 2026. Olivia's note, [docs/test-design/oracles.md](../test-design/oracles.md), records the properties, the deduction failure and the limits of each comparison.

A separate net-pay property found a deduction-sign defect: at a one-dollar deduction on $20 gross with zero tax, expected net was $19.00 and actual net was $21.00. The fix changed addition to subtraction. The property now generates deductions from 0 to 20 for a permanent employee's one-hour, non-holiday Monday shift at a rate of 20, so every generated deduction is within available pay. Margaret also decided that deductions above gross minus tax must be rejected with the employee name; deduction equal to available pay is allowed and yields zero net. These boundaries have explicit tests. This defect demonstrates a gap that the parallel run cannot cover.

## Decision

Freeze `legacy-engine` during migration. Run it and the refactored `pay-engine` on equivalent synthetic inputs in property tests. Compare gross, tax, retirement and leave by numeric `BigDecimal` value. Investigate each difference against the written rule and the known examples before changing either implementation.

Keep independent properties for net equals gross minus tax minus deductions, nonnegative tax, gross pay not decreasing when the tested shift grows by one minute, and ABA payment round trips. Keep concrete boundary and regression tests alongside generated tests. Extend generators when a new input class or migration risk appears. Reject deductions above available pay instead of silently reducing them or returning negative net pay.

Agreement with the frozen engine supports behavioral preservation within the generated domain, but does not prove that the inherited award or tax rules are correct. The tax schedule used here is a synthetic example, not an official tax table. Margaret remains the decision maker for intended payroll behavior.

## Alternatives considered

- Write expected gross and tax for every generated case: infeasible for a large generated input space and prone to copying the implementation into the test.
- Use only the frozen engine: cannot examine deductions, which its interface does not accept; shared rule defects also pass.
- Use only high-level properties: necessary but too weak to pin exact legacy behavior such as rounding and leave accrual.

## Consequences

The migration has a repeatable comparison over more inputs than a small set of hand-written examples. The frozen module adds a test dependency and needs deliberate review before any change. The parallel generator currently omits multi-shift and cross-midnight cases; a separate regression test fixes the expected result for a shift across the Sunday-to-Monday week boundary. Passing comparisons must be reported with their generator limit. A difference is a reason to review, not automatic proof that the new result is wrong.

The ABA property varies only the amount of a single payment, from 1 to 9,999,999 cents over 100 runs, and compares the returned payment list. It does not establish external bank acceptance or exclude shared writer/parser defects. The current available-pay guard rejects an individual calculation; callers must supply the employee name for a useful error, since the unnamed overload uses `unknown employee`. Margaret's requirement to reject the whole pay run still needs integration checks for batch rejection and rollback.
