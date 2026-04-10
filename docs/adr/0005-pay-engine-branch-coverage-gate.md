English | [简体中文](0005-pay-engine-branch-coverage-gate.zh-CN.md)

# ADR 0005: Gate pay-engine branch coverage at 65%

Ravi Menon — 2026-04-10

## Status

Accepted on 2026-04-10.

## Context

The team plans to refactor the inherited award rules after the boundary tests established the daily and calendar-week overtime limits. Olivia's control-flow note, [docs/test-design/overtime-control-flow.md](../test-design/overtime-control-flow.md), shows that existing tests exercise all five source statements and all four decision outcomes in the numbered `isOvertime` implementation. JaCoCo reports LINE 3/3, INSTRUCTION 12/12 and BRANCH 4/4 for that method. This local result does not describe the rest of `pay-engine`.

A clean run of `./mvnw -B -ntp clean verify` measured 49 covered and 19 missed branches for the `pay-engine` bundle, or 72.06%, in `pay-engine/target/site/jacoco/jacoco.xml`. All 45 `pay-engine` tests and 15 `bank-file` tests passed; these counts include parameterized invocations.

The inherited `overtimeFactor` condition had moved unchanged from the desktop engine's minute loop. Before the holiday regression test and fix, it had LINE 2/2 and INSTRUCTION 12/12 covered, but only BRANCH 3/4. Ordinary overtime reached `factor < tier` true; `shiftCrossingSundayMidnightUsesNextWeeksThreshold` reached both comparisons false. The missing outcome was `factor > 2.0` true. A nine-hour public-holiday shift at $10/hour exposed $195.00 instead of $202.50. `holidayOvertimeKeepsHigherHolidayFactor` failed before the fix and passed afterward. Removing `|| factor > 2.0` preserves the higher holiday factor; the fixed method has LINE 2/2, INSTRUCTION 8/8 and BRANCH 2/2. Full line and instruction coverage did not establish correct pay.

The current engine counts overtime minutes and tiers by calendar week and accumulates minute multipliers with `BigDecimal`.

## Decision

Set a JaCoCo `BRANCH` `COVEREDRATIO` minimum of `0.65` for the `pay-engine` bundle. Run `./mvnw -B -ntp verify` in the pull-request workflow so tests, the report and the gate run together. In review, inspect uncovered branches near changed award rules and require assertions against expected pay amounts for changed behavior. Treat the gate as a floor, not proof of correct payroll.

In `pay-engine/pom.xml`, retain `prepare-agent` and keep the HTML/XML `report` bound to `verify`. Add the `branch-gate` execution of `jacoco:check` at `verify` with a `BUNDLE` rule. The threshold applies to `pay-engine`, not `bank-file`.

## Alternatives considered

- **No gate:** a coverage drop could pass without a visible signal. The team would still review tests, but would lose this mechanical floor.
- **100% bundle branch gate:** inherited calculation and CLI defensive paths would make every unrelated change depend on covering all bytecode outcomes. The number of outcomes can change when code is rewritten, even when business behavior does not.
- **Statement or line gate:** the holiday defect remained despite all three source statements and both source lines of the inherited method being exercised. These counters would not have pointed to its missing condition outcome.

## Consequences

Pull requests below the bundle threshold fail verification. The current 49/68 branch result is above the threshold, with 19 branches still uncovered. Reviewers must examine the report and assertions; a passing build can contain a wrong payment. Local clean verification passed the check; each pull request must supply its own CI result.
