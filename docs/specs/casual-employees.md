English | [简体中文](casual-employees.zh-CN.md)

# Casual employees

Margaret O'Connell confirmed the rules for this example award. Hannah Schmidt implemented them on 2026-03-09.

- Each ordinary hour for a casual employee receives a loading of 25% of the base hourly rate, including ordinary weekend and public-holiday hours. Choose the day penalty first, then add 0.25 of the base rate. Public-holiday ordinary time is therefore 2.25 + 0.25 = 2.50 times base, not 2.25 × 1.25.
- Overtime receives the higher of the overtime tier and the day penalty, without casual loading. The first two overtime hours use 1.5× and subsequent overtime uses 2×.
- Each valid casual shift is paid for at least three hours. For a shorter shift, credit the missing minutes immediately after its actual end and apply the date and overtime rules for those minutes. The shift's `publicHoliday` flag continues to apply throughout its paid minutes. Zero or negative duration is invalid.
- Casual employees accrue no paid annual leave. Permanent employees have no three-hour minimum and accrue leave under the ordinary rules.

The calculation accepts shifts of 1 through 1,440 whole minutes. Test inputs use whole minutes. At present, weekly hours and overtime-tier minutes accumulate across a single `calculate` call; callers must keep that calculation within one calendar week. This is a calculation scope limitation, not a validated rejection of multi-week input.
