English | [简体中文](assessment-findings.zh-CN.md)

# Security findings handed to the external assessment

This register is what the team compiled for the external security assessment that
begins on 2026-08-27. Every item is a finding already found and closed in this
repository before the assessment, with the commit that fixed it and the regression
that now guards it. It is the team's own record of known issues and their closure,
not the assessors' report; the assessors draw their own conclusions. All data in the
repository is synthetic.

Commit identifiers are the short SHAs in this repository's history. Threat-model rows
refer to [docs/security/threat-model.md](threat-model.md).

| # | Finding | Severity | Threat-model row | Fix commit | Guarding regression |
| --- | --- | --- | --- | --- | --- |
| 1 | Employee search concatenated its text into SQL, so crafted text could match or expose other rows | High | Search text to SQL | `831917e` | `IntegrationEmployeeSearchSecurityTest` (added by `5490183`) |
| 2 | Employee search was not scoped to the caller's employer, so it could read another employer's rows | High | API to PostgreSQL | `0a0c64f` | `IntegrationEmployeeSearchSecurityTest` (`5490183`) |
| 3 | Error responses carried internal details (stack and exception text) to the caller | Medium | Browser to API | `92c1812` | `SystemErrorBoundaryTest` |
| 4 | Timesheet upload read the whole request without size, row or encoding bounds, risking resource exhaustion | Medium | CSV upload to API | `defbf2f` | `SystemTimesheetUploadRobustnessTest` |
| 5 | Overlapping or duplicate shifts for one employee were accepted, paying the same work twice | Medium | CSV uploads for the same employee | `c069019` | `SystemTimesheetOverlapTest` |
| 6 | The converter copied an account name into a fixed output field without bounds, overflowing a stack buffer | High | Old export to aba-convert | `bdd95d6` | `legacy/aba-convert/tests/run-sanitizers.sh` replays `fuzz/crashes/name-overflow.txt` (`da82df6`) |
| 7 | The converter accumulated the batch total in a type that could overflow, corrupting the written total | High | Old export to aba-convert | `4b12656` | `legacy/aba-convert/tests/run-sanitizers.sh` replays `fuzz/crashes/total-overflow.txt` (`10232cb`) |
| 8 | The ABA reader kept an inherited desktop reversal-reference branch that accepted malformed references | Low | Local ABA to bank handoff | `2bcdc80` | `bank-file` `AbaReferenceFuzzTest` seed `crash-reversal-reference` |
| 9 | A silently truncated desktop export was converted as if complete | Medium | Local ABA to bank handoff | `5f20f99` | `legacy/aba-convert/tests/control-trailer.sh`, `run-reconcile-cases.sh` |
| 10 | A detail amount field with trailing spaces was accepted instead of rejected | Low | Local ABA to bank handoff | `0b4370a` | `legacy/aba-convert/tests/amount-trailing-space.sh` |

## Notes

- Severity is a qualitative judgement of impact and reachability on this system, not a
  scored vector. The two converter memory-safety fixes (findings 6 and 7) concern a local
  tool that some sites still run on an older build, so they also need a published
  advisory with a scored vector.
- Findings 6, 7, 9 and 10 are in the inherited desktop converter `aba-convert`; the
  hosted service does not run it. Findings 1–5 are in the hosted API.
- Each fix landed with its guarding regression on the same branch; the differential
  and parser fuzzers, the sanitizer replays and the reconciliation cases keep these
  closed. A fix with a regression is closed against recurrence, not a guarantee that no
  related issue remains; the assessment looks for what this list does not cover.
