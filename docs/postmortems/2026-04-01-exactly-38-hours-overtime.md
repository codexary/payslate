English | [简体中文](2026-04-01-exactly-38-hours-overtime.zh-CN.md)

# Exactly 38 hours classified as overtime

Ravi Menon — 2026-04-01

## Summary

Chloe Martin brought a support case in which a permanent employee working exactly 38 hours in one week was paid overtime. Margaret O'Connell confirmed that weekly overtime starts after 38 hours. Olivia Brooks added the regression, and Hannah Schmidt corrected the comparison. Boundary review also led to separate calendar-week counters.

## Impact

For the synthetic five-shift case at $20/hour, 8+8+8+8+6 hours should produce $760.00 gross and no overtime. The failing regression produced $760.17, an excess of 17 cents. We know this calculation is reproducible; we do not know the number of affected customers, actual excess payments or remediation status from this example.

## Timeline

- 2026-02-23 — The imported desktop engine already used `weeklyMinutes >= 38*60`, with the current minute counted first.
- 2026-03-09 — The casual rules were implemented; the inherited weekly comparison remained.
- 2026-03-10 — Casual partition tests checked representative employment, day and duration classes, but not the exact weekly threshold.
- 2026-03-23 — Olivia added `exactlyThirtyEightHoursInWeekHasNoOvertime`, exposing $760.17 against $760.00. Hannah changed the weekly comparison to `>`.
- 2026-03-24 — Olivia added half-cent pay cases in `PayBoundariesTest.payRoundBoundary`, exposing a separate floating-point rounding failure.
- 2026-03-25 — Hannah replaced the `double` pay accumulator with exact weighted pay using integer quarter-rate minutes.
- 2026-03-26 — Tuan Nguyen ported the desktop ABA writer and parser into `bank-file` as `AbaFile`.
- 2026-03-27 — Olivia recorded the [boundary table](../test-design/boundary-values.md) and added neighbouring half-cent cases.
- 2026-03-30 — Hannah made weekly hours and overtime-tier minutes accumulate by each minute's Monday-starting calendar week and switched pay weights to exact decimal accumulation. The two-week and Sunday-to-Monday tests check the weekly threshold reset.
- 2026-03-31 — Ravi accepted [ADR 0004](../adr/0004-boundary-matrix-for-pay-and-aba.md).
- 2026-04-01 — The team recorded this review.

## Root cause and trigger

The trigger was a week reaching exactly 2,280 minutes. The current minute was counted before an inclusive comparison classified it as overtime. The daily comparison reads the count before adding the current minute, which is why its `>=` remains correct: the first daily overtime minute is minute 481, while the first weekly overtime minute is minute 2,281.

The older weekly and overtime-tier counters also spanned a whole calculation rather than individual calendar weeks. That is a separate scope problem: fixing equality alone does not reset the counters at Monday midnight.

## Resolution and checks

The weekly comparison uses `>`, and each paid minute now belongs to the week beginning on its Monday. `exactlyThirtyEightHoursInWeekHasNoOvertime` asserts $760.00 gross and zero overtime. `weeklyThresholdResetsForSecondWeekOfPayPeriod` asserts $1,520.00 and zero overtime over two 38-hour weeks. `shiftCrossingSundayMidnightUsesNextWeeksThreshold` asserts $785.00 and 0.5000 overtime hours for 38 prior hours plus a shift crossing Sunday midnight.

The suite contains 59 cases: 44 in `pay-engine` and 15 in `bank-file`, counting parameterized invocations. The cross-week tests check threshold reset; they do not independently demonstrate overtime-tier reset. Local calculations and ABA checks do not establish that customer payments have been corrected or accepted by a bank.

## What went well

The exact shifts and rule allowed an independent expected amount. The regression checks both pay and overtime classification. Comparing the neighbouring minute cases made the equality error visible.

## What we missed

Earlier representative cases did not include equality at 38 hours or a calculation crossing calendar weeks. Our test selection left both the equality decision and counter scope insufficiently checked. The review concerns those protections, not individual blame.

## Where the example helped

Ordinary weekday shifts kept daily overtime and penalty rates from obscuring the weekly rule.

## Action items

- Olivia: retain the boundary table and neighbouring-minute checks; add a case that independently exercises the overtime-tier reset across weeks.
- Margaret: review whether equality belongs inside or outside each threshold when rules change.
- Hannah: preserve the regressions and per-minute calendar-week assignment when changing the engine.
- Chloe and Margaret: establish affected pay runs, payment impact and correction status through customer support.
