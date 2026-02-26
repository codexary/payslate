English | [简体中文](2026-02-26-midnight-weekend-underpayment.zh-CN.md)

# Cross-midnight weekend underpayment

Ravi Menon — 2026-02-26

## Summary

Chloe Martin relayed a café chain's report after the last desktop release: a Friday evening shift ending on Saturday had been paid without the Saturday penalty. Margaret O'Connell confirmed the pay rule. The reproduction keeps the reported shift times with a synthetic permanent employee at $20/hour, Friday 2026-02-13 23:00 to Saturday 2026-02-14 01:00, without earlier hours or a public-holiday flag. The inherited engine produced $40.00 gross; the rule requires $45.00.

## Impact

The reproduced case underpays $5.00. We have not established the number of affected employees or pay runs, the total underpayment, or the customer's correction status. Those questions need customer support follow-up; one reproduction cannot establish their answers.

## Timeline

- Before the regression work, Chloe brought the desktop customer's report to the team and Margaret checked the rule. The exact report and desktop release times are not recorded here.
- 2026-02-23 — Hannah Schmidt imported the Payslate 7 engine, build and CI. It supported permanent employees only.
- 2026-02-24 — Olivia Brooks added `cafeFridayShiftCrossingMidnightPaysSaturdayPenalty` and the [test-design note](../test-design/weekend-penalty.md). Expected gross was $45.00; the inherited calculation returned $40.00.
- 2026-02-24 — Hannah changed day selection to read the current date inside the paid-minute loop. The `pay-engine` suite now has four tests, including the café regression.
- 2026-02-25 — Ravi Menon accepted [ADR 0001](../adr/0001-characterise-legacy-pay-before-rewrite.md), retaining independently calculated regression cases before broader changes.
- 2026-02-26 — The team recorded this review and the follow-up actions below.

## Root cause and trigger

The inherited routine evaluated `s.start().getDayOfWeek()` once for the shift. When the clock crossed midnight, the date used for daily hours advanced, but the day used for penalty rates stayed `FRIDAY`. The trigger was one shift crossing Friday into Saturday. Existing weekend tests started separately on Saturday and Sunday rather than carrying the same shift across midnight; they could pass while the cross-day fault remained.

## Resolution

The engine now reads `date.getDayOfWeek()` for every paid minute. The regression checks $20 for the Friday hour plus $25 for the Saturday hour, and remains in pull-request CI. Customer payroll correction and any wider backfill need separate operational tracking; this code correction does not establish that they are complete.

## What went well

The reported timing could be reduced to a small synthetic test with an independently checked amount. Olivia's regression preceded Hannah's focused fix, making the failure and the correction inspectable separately.

## What we missed

The initial tests checked weekend rates but not a change of day inside a shift. They did not distinguish per-shift day selection from per-minute day selection. This was a gap in our test protection, not a conclusion about an individual's care.

## Where the example helped

The two-hour shift isolated midnight's rate change without daily or weekly overtime obscuring the result. It does not tell us the size of any wider effect.

## Action items

- Olivia: retain the café regression and add other cross-day cases as rules are confirmed.
- Hannah and Kwame Mensah: inspect other uses of shift-start values that may become stale as the calculation clock advances.
- Margaret and Chloe: establish affected customer pay runs and correction status in support records.
- Ravi: keep the regression in pull-request CI and review it before related changes are merged.
