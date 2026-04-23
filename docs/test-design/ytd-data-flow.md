English | [简体中文](ytd-data-flow.zh-CN.md)

# YTD def-use review

Olivia Brooks — 2026-04-23

## The ported routine

`YtdAccumulator` was ported unchanged from the Payslate 7 desktop product into `pay-engine` on 2026-04-20. Its reset on negative adjustments was an inherited defect. Before the fix, the calculation after the two parameter null checks was:

```java
BigDecimal total = yearToDate;                      // d1, use y0/y1
if (adjustment.signum() < 0) total = BigDecimal.ZERO; // d2, use a0 in the condition
total = total.add(gross).add(adjustment);            // d3, use d1/d2, g0, a0
yearToDate = total;                                 // y1, use d3
return total;                                       // use d3
```

`yearToDate` starts at `BigDecimal.ZERO` (`y0`), then each successful call redefines it at `y1`. Parameters `gross` (`g0`) and `adjustment` (`a0`) are defined at method entry. `Objects.requireNonNull` reads each parameter before the calculation. The definitions of `total` are `d1` (copy previous YTD), `d2` (incorrect reset on a negative adjustment), and `d3` (computed new total). A definition reaches a use only if no intervening definition of the same variable overwrites it.

## Feasible def-use pairs before the fix

Both tests are in `YtdAccumulatorTest`:

- **Original test:** `accumulatesOrdinaryAndPositiveAdjustment` calls `add(100.00, 0)` and then `add(50.00, 10.00)`, asserting `100.00` and `160.00`.
- **Regression:** `negativeAdjustmentRetainsEarlierPay`, added on 2026-04-21, calls `add(100.00, 0)` and then `add(50.00, -10.00)`, asserting that the second result is `140.00`.

Each test creates a fresh accumulator. The table describes the paths each test exercises in the ported routine, including the regression's execution before its assertion fails.

| Variable | Feasible def-use pair | Original test | Regression |
| --- | --- | --- | --- |
| `yearToDate` | `y0 → d1` on the first call | Covered | Covered |
| `yearToDate` | `y1 → d1` on the next call | Covered | Covered |
| `total` | `d1 → d3` when adjustment is nonnegative | Covered | Covered on the first call |
| `total` | `d2 → d3` when adjustment is negative | Missing | Covered on the second call |
| `total` | `d3 → y1` | Covered | Covered |
| `total` | `d3 → return` | Covered | Covered |
| `gross` | `g0 → null check` | Covered | Covered |
| `gross` | `g0 → d3` | Covered | Covered |
| `adjustment` | `a0 → null check` | Covered | Covered |
| `adjustment` | `a0 → signum` | Covered | Covered |
| `adjustment` | `a0 → d3` | Covered | Covered |

Counting `y0, y1, d1, d2, d3, g0, a0`, the original test exercises a definition-clear path from six of the seven definitions to a use: **all-defs 6/7**. It covers **10/11** listed feasible def-use pairs; only `d2 → d3` is missing, so **all-uses is not satisfied**. The regression alone covers **all-defs 7/7** and **all-uses 11/11** in this model: its first call covers `d1 → d3`, and its second covers `d2 → d3`. Coverage does not mean the assertion passes.

These counts include method-entry and field definitions and the null-check uses. `signum` counts as one use site, rather than separate true and false outcomes. Both tests supply non-null arguments; neither asserts the null-check exception behavior.

## Regression, fix and refactor

Before the fix, the regression returned **40.00 instead of 140.00**. On its second call, `d1` copied the earlier `100.00`, but `d2` overwrote it with zero before `d3` read `total`. Both paths reached the same addition statement, yet the negative path lost the earlier pay.

The fix on 2026-04-21 changed the negative branch to `total = yearToDate`, preserving the previous total. Both tests then passed. This assignment repeated the value already copied at `d1` and added another read of `yearToDate`; the table above describes the pre-fix routine, not this intermediate version.

The refactor on 2026-04-22 removed the redundant branch and local `total`. The current method retains both null checks and uses one field assignment:

```java
yearToDate = yearToDate.add(gross).add(adjustment);
return yearToDate;
```

Both tests still pass. Each successful call reads the prior YTD, adds gross pay and the adjustment, and writes the field once. The old `total` definitions `d1`, `d2` and `d3` no longer exist, so the old 11-pair denominator does not apply to this implementation. The tests establish these two-call examples, not every arithmetic, currency, rounding or year-rollover rule.
