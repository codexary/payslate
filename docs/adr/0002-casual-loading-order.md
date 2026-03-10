English | [简体中文](0002-casual-loading-order.zh-CN.md)

# ADR 0002: Add casual loading to the base rate after the day penalty

## Status

Accepted on 2026-03-11.

## Context

The casual rule says ordinary hours receive 25% loading, including weekends and public holidays. It did not originally state whether the loading multiplies the day penalty or adds 0.25 of the base rate. Those interpretations produce different pay. Margaret O'Connell confirmed the intended rule in `docs/specs/casual-employees.md`.

## Decision

For each ordinary minute, choose the day factor and add 0.25 of the base hourly rate for a casual employee. A public-holiday ordinary minute therefore uses 2.25 + 0.25 = 2.50 times base. Overtime uses the higher of the overtime tier and day penalty, without casual loading. Keep `casual_public_holiday_interaction` and `casual_overtime_no_loading` as named regression partitions.

## Alternatives considered

- Multiply the day factor by 1.25. This would make a public-holiday ordinary hour 2.8125 times base and contradict Margaret's confirmed rule.
- Apply loading to overtime. This contradicts the same specification.

## Consequences

The day factor and employment type must be tested together. The written specification is the oracle; current engine output alone is insufficient to decide the rule. Any future award with another loading order needs its own explicit specification.
