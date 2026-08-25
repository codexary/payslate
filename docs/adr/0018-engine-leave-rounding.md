English | [简体中文](0018-engine-leave-rounding.zh-CN.md)

# ADR 0018: Keep the frozen engine as the oracle for paid amounts only

Ravi Menon — 2026-08-25

## Status

Accepted on 2026-08-25.

## Context

`DifferentialEngineFuzzTest` runs the frozen 2004 engine and the refactored
`PayEngine` against the same roster and compares their output. Both engines compute
the paid amounts — gross, tax, net and retirement — from exact integer minutes, so
those agree. They differ in how they carry the ordinary total that feeds the
displayed hours and the accrued leave: the frozen engine keeps a `double` that adds
`1.0/60.0` once per ordinary minute, while `PayEngine` divides the exact integer
minute count.

Over a month of shifts the fuzzer found a roster on which the money is identical but
the accrued leave differs by 0.0001 hour. Walking the ordinary-minute total from 1 to
12000 and rounding each side the way each engine does (the `double` sum, versus an
exact divide to ten places, each multiplied by the `0.076923` accrual rate and rounded
HALF_EVEN to four places) shows the leave values separate at exactly four totals:

| ordinary minutes | frozen (double) | refactored (exact) |
| --- | --- | --- |
| 3000 | 3.8461 | 3.8462 |
| 7000 | 8.9743 | 8.9744 |
| 9000 | 11.5385 | 11.5384 |
| 11000 | 14.1026 | 14.1025 |

At 3000 minutes the `double` sum is 49.999999999998444, a hair below the exact 50, so
the frozen leave rounds down. But the difference does not have a fixed direction: at
9000 and 11000 minutes it is the `double` side that is the larger of the two. The gap
is always one unit in the fourth decimal (0.0001 hour). The displayed ordinary hours,
rounded to four places, hide it — both read 50.0000 at 3000 minutes — because only the
leave multiplies the total by the accrual rate and lands on a different HALF_EVEN
boundary.

## Decision

The refactored `PayEngine` is authoritative for every value it produces, including the
displayed hours and the accrued leave. The frozen engine is retained only as a
differential oracle for the paid amounts — gross, tax, net and retirement — and not for
the displayed hours or the leave. The differential test therefore compares the paid
amounts and the payslip invariants, and a separate regression pins the known leave
difference on the roster above so it cannot drift silently.

The payroll rule is that leave accrues on ordinary hours; the exact integer
computation is the correct realisation of that rule, and a 0.0001-hour difference in a
displayed figure, at a few specific totals, is an accepted rounding artefact of the
frozen engine rather than a defect to fix in either engine. Margaret signed off on
treating the exact side as correct.

## Alternatives considered

- **Change the frozen engine to accumulate exactly.** Rejected. The frozen engine is
  the migration baseline; it is kept byte-for-byte as the system being replaced, and
  editing it would defeat the point of comparing against it.
- **Reintroduce the `double` accumulation in `PayEngine` so the two match.** Rejected.
  That trades a correct exact result for a reproduction of the floating-point error.
- **Treat any divergence, leave included, as a release blocker.** Rejected. The money
  is correct and equal; blocking the migration on a 0.0001-hour cosmetic difference at
  four totals would stall it for no payroll consequence.
- **Narrow the oracle to the paid amounts and record the leave difference (chosen).**
  Keeps the migration guarantee that matters — the money is identical — while writing
  the known display difference down where a reviewer can see it.

## Consequences

- The differential oracle no longer compares displayed hours or leave; it compares the
  paid amounts and the invariants (net equals gross minus tax minus deductions; gross
  and tax are non-negative).
- `LeaveRoundingDifferenceTest` records both engines' leave on the found roster, so a
  future change to either rounding path fails a test instead of passing unnoticed.
- The oracle cannot catch an error shared by both engines (for example a wrong entry in
  the common withholding table), and the invariants cannot tell whether the tax amount
  itself is right. Those are covered by the schedule proof and the boundary tests, not
  here.
