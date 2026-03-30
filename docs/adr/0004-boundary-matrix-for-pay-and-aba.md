English | [简体中文](0004-boundary-matrix-for-pay-and-aba.zh-CN.md)

# ADR 0004: Boundary matrix for pay, calendar weeks and ABA fields

## Status

Accepted on 2026-03-31.

## Context

A support case showed a permanent employee classified as working overtime at exactly 38 weekly hours. `exactlyThirtyEightHoursInWeekHasNoOvertime` expected $760.00 and exposed $760.17. The inherited weekly comparison was `>=`, counting the current minute before comparing. The pay engine also has daily, overtime-tier, casual-minimum, tax-schedule and cent-rounding transitions. Broad partition representatives do not establish which side owns equality.

On 2026-03-26, Tuan Nguyen ported the Payslate 7 desktop ABA writer and parser into `bank-file` as `AbaFile`. Its amount and BSB fields require format boundaries as well as numerical checks.

## Decision

For each numerical or format transition, record the smallest legal step where one exists, the on point, an off point in the opposite class, the other neighbour and an observable assertion in `docs/test-design/boundary-values.md`. When rounding makes adjacent outputs equal, include an input that exposes the formula change. Use JUnit 5 parameterized tests for repeated cases, and assert gross pay together with overtime hours at time boundaries.

Keep the weekly comparison as `>`. Since 2026-03-30, the engine assigns each paid minute to its Monday-starting calendar week and accumulates both the weekly threshold and overtime tier within that week. `weeklyThresholdResetsForSecondWeekOfPayPeriod` checks two complete 38-hour weeks: $1,520.00 and zero overtime. `shiftCrossingSundayMidnightUsesNextWeeksThreshold` checks 38 prior hours plus Sunday 23:30 to Monday 00:30: $785.00 and 0.5000 overtime hours. These two tests check threshold reset; they do not independently exercise the two-hour overtime-tier reset across weeks.

Accumulate exact decimal rate weights and round gross half-even at the total. Withholding rounds half-up. For ABA, check rejection, record width, exact field positions and parse round trips; also test independently corrupted records so shared writer/parser assumptions cannot be the only oracle.

## Alternatives considered

- Keep only the support regression. It catches the weekly comparison but leaves other transitions unchecked.
- Test many arbitrary values. More tests can still miss equality and width limits.
- Require callers to split every calculation by week. This makes correctness depend on every caller and complicates shifts crossing Sunday midnight.
- Rely only on writer/parser round trips. Shared mistakes can accept the same malformed field on both sides.

## Consequences

Margaret and Olivia can review the rule boundaries directly. The suite contains 59 cases: 44 in `pay-engine` (5 in `LegacyPayEngineTest`, 11 in `CasualPartitionsTest`, 28 in `PayBoundariesTest`) and 15 in `AbaFileTest`. These counts include parameterized invocations. They describe coverage, not proof of every rule. The tax checks use the synthetic example schedule, and the ABA checks validate local format behaviour; they do not establish official tax compliance or bank acceptance.
