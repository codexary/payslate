English | [简体中文](oracles.zh-CN.md)

# Payroll oracles

Olivia Brooks — 2026-06-05

`legacy-engine` is the frozen Payslate 7 calculator; its source differs only in package declaration. `PayEnginePropertiesTest.parallelRunOracle` generates rates, shift lengths, employment types, holidays and days and compares gross, tax, retirement and leave over 100 runs. The new path calculates money with `BigDecimal` and dates with `java.time`. Agreement is a migration oracle, not proof that inherited award rules are correct.

The comparison uses numeric `BigDecimal` equality. Its inputs are single shifts starting at 08:00 on 22–28 February 2026, lasting 1–600 minutes, with integer hourly rates of 1–500, permanent or casual employment, and either holiday flag. Deductions are zero because the frozen interface has no deduction input. The generator does not cover multiple shifts or midnight crossings; `refactoredEngineResetsWeeklyOvertimeAcrossSundayMidnight` separately checks a Sunday-to-Monday shift after 38 hours in the preceding week, expecting gross of $785.00 and 0.5000 overtime hours at $20/hour.

The net-pay property found a real deduction-sign defect. jqwik printed:

```text
Shrunk Sample (1 steps)
-----------------------
  arg0: 1

Original Sample
---------------
  arg0: 64
```

For one dollar of deductions on a $20 gross payslip, expected net was $19.00 and actual net was $21.00. The fix changes addition to subtraction: `gross.subtract(tax).subtract(deductions)`. The original sample can vary between randomized runs. `netEqualsGrossMinusTaxMinusDeductions` initially generated deductions from 0 to 100; after the available-pay limit was added, its range became 0–20 for a permanent employee's one-hour, non-holiday Monday shift at $20/hour. The example tax schedule gives zero tax on that gross, so every generated deduction is within available pay.

Other properties check monotone gross, nonnegative tax, and ABA payment round trips:

- `moreHoursNeverLowerGross` compares a 1–600-minute shift with the same shift extended by one minute, for a permanent employee at $20/hour on a non-holiday Monday.
- `taxIsNeverNegative` varies the integer hourly rate from 1 to 1,000 for a permanent employee's one-hour, non-holiday Monday shift. The tax schedule is a synthetic example, not an official tax table.
- `AbaPropertiesTest.abaRoundTripPreservesPayments` runs 100 generated amounts from 1 to 9,999,999 cents in a single-payment batch. The other fields are fixed. It checks that `parse(write(batch)).payments()` contains exactly the original payment. This checks payment preservation, not every batch field or external bank acceptance; matching writer and parser defects can escape it.

Margaret's rule is to reject a pay run when deductions exceed gross minus tax, naming the employee in the error. Net pay may be zero but must never be negative. The current `PayEngine.calculate` enforces the limit for an individual calculation: `deductionsAtAvailablePayYieldZeroNet` accepts $20.00 of deductions on $20.00 available pay, while `deductionsOverAvailablePayRejectNamedEmployee` rejects $20.01 with `Deductions exceed available pay for employee Alice Test`. The overload without an employee name uses `unknown employee`. These tests do not establish whole-batch rejection or rollback; that remains to be checked when calculations are integrated into pay runs.
