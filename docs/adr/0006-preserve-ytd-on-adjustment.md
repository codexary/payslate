English | [简体中文](0006-preserve-ytd-on-adjustment.zh-CN.md)

# ADR 0006: Preserve prior YTD on an adjustment

Ravi Menon — 2026-04-24

## Status

Accepted on 2026-04-24.

## Context

Payslate Online ported `YtdAccumulator` unchanged from the Payslate 7 desktop product into `pay-engine` on 2026-04-20. The inherited routine copied `yearToDate` into local `total`, then reset `total` to `BigDecimal.ZERO` on a negative `adjustment`. The next statement added `gross` and `adjustment` to that zero and wrote the result back to `yearToDate`. The reset defect was inherited from the desktop product.

The original test, `accumulatesOrdinaryAndPositiveAdjustment`, covered zero and positive adjustments. Olivia's data-flow note, [docs/test-design/ytd-data-flow.md](../test-design/ytd-data-flow.md), records the untested `d2 → d3` pair on the negative path. The regression `negativeAdjustmentRetainsEarlierPay` first adds `100.00`, then adds `50.00` with `-10.00`; it expects `140.00`. Before the fix on 2026-04-21, the inherited implementation returned `40.00`. Both tests pass after the fix and the 2026-04-22 refactor.

## Decision

A negative adjustment changes the amount added on this call; it does not reset prior YTD. Each `add` call reads the previous `yearToDate`, adds `gross` and `adjustment`, and writes the field at one point. Keep both the ordinary/positive and negative-adjustment tests.

Retain the current implementation's null checks and single calculation assignment, `yearToDate = yearToDate.add(gross).add(adjustment)`, followed by `return yearToDate`.

## Alternatives considered

- Keep the reset and document it as a new-year operation. Rejected: this method has no year boundary input, and the regression test demonstrates the loss of an earlier amount on an ordinary correction.
- Replace zero with `yearToDate` while retaining the conditional. This was the first fix, but the assignment repeated the value already copied into `total`. The following refactor removed the redundant branch.

## Consequences

- The correction test pins the two-call behavior, including a nonzero prior YTD.
- The current method has no negative-adjustment branch or local `total`; its data-flow targets differ from those of the inherited routine. The note's pre-fix 11-pair model does not measure coverage of the current method.
- This decision covers the accumulator's stated arithmetic only. Year rollover, currency rules, and monetary rounding require separate specifications and tests.
