English | [简体中文](weekend-penalty.zh-CN.md)

# Café shift crossing into Saturday

Olivia Brooks — 2026-02-24

`LegacyPayEngineTest.cafeFridayShiftCrossingMidnightPaysSaturdayPenalty` keeps the times of the reported shift, from Friday 2026-02-13 23:00 to Saturday 2026-02-14 01:00. The employee is synthetic: permanent, earns $20/hour, has no earlier hours, and the shift is not a public holiday.

The independent gross-pay oracle is one Friday hour at $20 plus one Saturday hour at $20 × 1.25: **$45.00**. The inherited engine returns **$40.00**, an underpayment of **$5.00**.

| Term | Observed chain |
| --- | --- |
| Fault | `day = s.start().getDayOfWeek()` is evaluated once per shift. |
| Error state | At 00:00 on Saturday, the loop still holds `FRIDAY` for the current minute, although `clock.toLocalDate()` has advanced. |
| Failure | Gross pay is $40.00 instead of $45.00. |

`saturdayAndSundayPenalties` starts each shift on the day whose rate it checks. It cannot distinguish a rate chosen once per shift from a rate chosen for every minute. This regression keeps one shift intact across midnight and asserts the independently calculated gross amount. The required behaviour is to select the day rate for each paid minute; the failing test must remain when the engine is corrected.
