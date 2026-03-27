English | [简体中文](boundary-values.zh-CN.md)

# Boundary-value analysis for pay and ABA

Olivia Brooks — 2026-03-27

The synthetic support case is a permanent employee working 8+8+8+8+6 hours Monday through Friday at $20/hour. The inherited weekly comparison used `weeklyMinutes >= 38*60`; it counted the current minute first and classified minute 2,280 as overtime. `exactlyThirtyEightHoursInWeekHasNoOvertime` expected **$760.00** and exposed **$760.17**. The fault is the inclusive comparison, the error state is the final minute marked as overtime, and the failure is 17 cents excess gross pay. The fix uses `>`.

Time cases use whole minutes, so the nearest neighbour is one minute away. For the overtime target class, 8 daily hours and 38 weekly hours are on points outside the class; the following minute is an off point inside it, and the preceding minute is an additional neighbour. Each case starts a fresh calculation and holds other conditions ordinary unless the target requires otherwise. Monetary examples below are dollars except the ABA amount row, which is cents.

| Boundary | Below / preceding neighbour | On point | Above / following neighbour | Expected observation |
| --- | --- | --- | --- | --- |
| Daily 8 h | 7 h 59 m: $159.67 | 8 h: $160.00 | 8 h 1 m: $160.50 | First minute beyond 8 h uses 1.5×; overtime hours are 0.0000, 0.0000, 0.0167. |
| Weekly 38 h | 37 h 59 m: $759.67 | 38 h: $760.00 | 38 h 1 m: $760.50 | Four prior 8 h shifts; overtime hours are 0.0000, 0.0000, 0.0167. |
| First overtime tier 2 h | Total 9 h 59 m: $219.50 | Total 10 h: $220.00 | Total 10 h 1 m: $220.67 | Overtime minute 121 uses 2×; overtime hours are 1.9833, 2.0000, 2.0167. |
| Casual minimum 3 h | 2 h 59 m: $75.00 | 3 h: $75.00 | 3 h 1 m: $75.42 | Top-up applies below the bound only; all three have zero overtime. |
| Tax threshold $500 | $499.99: $0.00 | $500.00: $0.00 | $500.01: $0.00 | Schedule row changes at equality; cent rounding hides the change. $500.05 gives $0.01. |
| Tax threshold $1000 | $999.99: $50.00 | $1000.00: $50.00 | $1000.01: $50.00 | Schedule row changes at equality; cent rounding hides the change. $1000.025 gives $50.01. |
| Pay cent tie, half-even | $0.004 → $0.00 | $0.005 → $0.00 | $0.006 → $0.01 | One ordinary hour at these base rates; also $0.015 → $0.02 to check a tie rounding up to the even cent. |
| Tax cent tie, half-up | $500.049 → $0.00 | $500.050 → $0.01 | $500.051 → $0.01 | Direct `withholding` inputs; also $1000.025 → $50.01. |
| ABA amount, cents | 0 rejected; 9,999,999,998 accepted | 1 and 9,999,999,999 accepted | 10,000,000,000 rejected | Lower and upper inclusive bounds; smallest step is one cent. Ten digits, no sign or decimal. |
| ABA BSB shape | `00-000` rejected | `000-000` and `999-999` accepted | `0000-000` rejected | Three digits, hyphen, three digits; `000000` and `00A-000` are other invalid shapes. |

`PayBoundariesTest.hourBoundary` asserts gross and overtime hours together. `taxBoundary` calls `withholding` directly, so sub-cent inputs are deliberate probes, not claims about payslip precision. The tax schedule in `example-tax-schedule.csv` is synthetic. There is no declared smallest decimal input step for `BigDecimal`; the $0.001 spacing around the half-cent tie is a chosen probe spacing. `payRoundBoundary` checks exact weighted pay, currently accumulated as integer quarter-rate minutes and converted using `BigDecimal` with half-even rounding. Withholding uses half-up.

Tuan Nguyen ported the Payslate 7 desktop ABA writer and parser into `bank-file` as `AbaFile`. It writes 120-character records with CRLF: type 0 header, type 1 details, and type 7 totals. Payroll credits have a balancing debit. In a detail record, the amount occupies positions 21–30 and the BSB positions 2–8 (one-based). The writer restricts text fields to its allowed ASCII characters.

`AbaFileTest.amountBoundary` checks record width, the exact amount field and parse round trips for accepted amounts, and exceptions for rejected amounts. `bsbBoundary` checks acceptance or rejection and round trips. `rejectsTamperedRecord` checks wrong record length, a nondigit amount, inconsistent credit totals and an invalid BSB separator. These local checks do not establish bank membership for synthetic BSBs or acceptance by a bank.

Weekly and overtime-tier counters still span one `calculate` call. Calendar-week separation needs its own checks; the cases here do not establish cross-week correctness.
