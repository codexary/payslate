English | [简体中文](0011-seeded-calendar-campaign.zh-CN.md)

# ADR 0011: Keep a seeded calendar campaign and pay elapsed time

Ravi Menon — 2026-06-26

## Status

Accepted on 2026-06-26.

## Context

Payroll officers need calendar-derived pay periods, and staff must be paid for time actually worked across daylight-saving changes. The existing parallel comparison with the frozen migration oracle did not expose the inherited wall-clock defect: its generated shifts start at 08:00 on 22–28 February 2026 and last at most 600 minutes, so none crosses an offset change. A duration helper tested outside the real calculation would not establish correct payroll behavior.

`PayCalendarRandomTest` uses seed `202010310027` for 20,000 draws across 2019–2030, equal frequency weights, anchors from 1–14 January 2020 and Melbourne shifts. One eighth of the date draws are replaced with an April or October transition day; start hours range from 00–23 and elapsed lengths from 1–12 hours. Each trial checks calendar properties and calls the real `PayEngine` with CSV-style local endpoints. At zero-based trial 223, it exposed 8.0000 paid hours against 9.0000 elapsed hours. The corrected 01:00–09:00 examples pay 9.0000 hours and $270.00 on 2 April 2023, and 7.0000 hours and $210.00 on 1 October 2023.

Calling the unchanged frozen engine on the same draws produces 276 disagreements with the original instant oracle: 269 incorrect paid-hour results and seven rejections. Weekly, fortnightly and monthly counts are 93/6,590, 90/6,757 and 93/6,653. There are 259/2,560 disagreements for starts on transition days and 17/17,440 for other starts; all 276 cross an offset change. Customer operational profiles were not available, so these deliberately weighted sample proportions do not measure production failure probabilities.

The CSV contract loses information in 15 generated cases whose ends use the later offset in an overlap. The required earlier-offset resolution cannot reproduce their original instants. The corrected campaign retains all draws: 19,985 match the original instant oracle, eight check the shorter duration required by CSV resolution, and seven check rejection after resolution collapses the duration to zero. Those 15 policy cases are explicitly separate from raw-instant matches. The frozen-engine totals include them.

## Decision

Keep the fixed-seed 20,000-draw campaign as a regression test of calendar invariants and the real pay path, alongside explicit leap-day, 27-fortnightly-pay-date and daylight-saving examples. Derive calendar pay runs through `PayCalendar.periodsForYear`; the API system test verifies the stored inclusive period and rejection of an off-calendar pay date. Preserve the frozen migration engine unchanged; disagreement at a newly specified boundary must be assessed against the product rule, not resolved by copying the frozen behavior.

Pay elapsed minutes between resolved endpoint instants, validate the existing 1–1440-minute limit and apply the casual minimum to that duration. Advance a zoned clock so date, weekday and week rules remain local. Use one configured `payslate.time-zone`, defaulting to `Australia/Melbourne`, to construct the service's `PayEngine`. Ambiguous local times use the earlier offset and gap times move forward, following `atZone`. Report cases whose original offsets cannot survive CSV conversion separately. Per-employer zones and an input contract capable of preserving the later occurrence of an overlapping time remain open decisions.

State the seed, trial count, oracle and input distribution with every campaign result. Distinguish comparisons with original instants, CSV policy assertions and rejected inputs. A passing campaign establishes only that its observable assertions passed for that sample under those rules; it is not a production-risk estimate or a release guarantee. Keep release judgment with the full set of test results and the payroll-rule review.

Use `P(at least one detection) = 1 - (1-p)^n` only when trials can reasonably be treated as independent draws from a stable distribution and `p` is the stated per-trial probability of an oracle-visible failure. For target `c`, `n = ceil(log(1-c) / log(1-p))`. A hypothetical `p = 0.01` and `c = 0.95` require 299 trials, with calculated detection probability 0.950464. The separate simulation, seed `202010310028`, detected at least one event in 9,530 of 10,000 campaigns of 299 Bernoulli trials (0.9530). It checks the stipulated model, not payroll correctness or customer exposure.

Before estimating customer risk, obtain pay-frequency weights, zones, start-time and shift-length patterns, and offset-crossing proportions. Define the independent trial unit and account for correlations among repeated shifts from one employer or calendar. Do not substitute the deliberately weighted test sample for missing customer data.

## Alternatives considered

- Keep only named examples: they preserve known boundaries but do not explore combinations of year, frequency, anchor and shift.
- Test elapsed duration only in a calendar helper: that can pass while the product still pays wall-clock hours.
- Require agreement with the frozen engine everywhere: that would preserve its daylight-saving defect despite the elapsed-time rule.
- Remove the seed: changing samples may broaden exploration but makes a reported failure harder to replay. Additional seeds can be recorded separately.
- Resample unrepresentable overlap endpoints: that changes the distribution and obscures the input contract's information loss. Keep the exact draws and explicit policy checks instead.
- Treat 20,000 passes or the 299-trial model as release confidence: neither supplies a customer profile or an oracle for every kind of payroll error.

## Consequences

The campaign is repeatable and covers the actual calculation. Its calendar API checks and configured-zone checks also exercise the service, including uploaded transition shifts. It costs a 20,000-case run; a fixed seed repeats one sample rather than supplying independent replications. The frozen parallel run remains useful within its non-transition domain, but cannot validate the new time rule.

The CSV representation cannot express every elapsed shift in an overlap, even when the calculation follows the documented policy. Customers needing that distinction require an explicit input decision. A single zone also limits employers operating in other zones. The hours oracle cannot detect incorrect tax schedules, award interpretation, deductions or bank output; those retain separate checks. Full distribution, measured output and reproduction instructions are in [the random-calendar note](../test-design/random-calendars.md).
