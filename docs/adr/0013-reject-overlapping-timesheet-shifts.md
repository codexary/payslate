English | [简体中文](0013-reject-overlapping-timesheet-shifts.zh-CN.md)

# ADR 0013: Reject overlapping timesheet shifts and define duplicate-upload semantics

Ravi Menon — 2026-07-29

## Status

Accepted on 2026-07-29.

## Context

The final paragraph of [ADR 0012](0012-timesheet-upload-limits.md) left open a pay-correctness defect: uploading the same file twice pays its shifts twice, and overlapping shifts for the same employee are accepted. The product owner, Margaret O'Connell, prioritised fixing this defect.

A realistic trigger is a network timeout. The payroll officer's client retries the upload while the first request may still be running. A naive check followed by an insert allows both requests to find no existing shift and then insert the same shifts, causing duplicate pay.

## Decision

Treat shifts as half-open intervals [start, end). Two shifts for the same employee overlap when each starts before the other ends; adjacent shifts, where one ends exactly when the next starts, do not overlap.

Reject overlaps, naming the offending line and never echoing the row. An overlap within one uploaded file is a client error (400); an overlap with an already-stored shift is a conflict with existing state (409).

Resolve each row to its employee, then within the upload transaction lock those employee rows before checking for stored overlaps and inserting shifts:

```sql
SELECT id FROM employees WHERE employer_id = ? AND id IN (...) ORDER BY id FOR UPDATE
```

This makes a concurrent upload for the same employee wait rather than race, and works on both the H2 build and PostgreSQL. If the first upload commits, re-sending the same file after a network timeout therefore returns 409 and names the line, also telling the officer that the file was already imported. If the first request is still running, the retry waits for its transaction to finish before checking stored shifts.

Do not introduce a content-level idempotency key yet. It needs a stable business key identifying an upload and a deduplication store; overlap rejection already prevents duplicate pay. Revisit it if partial re-sends become common, such as a file that adds some new, non-overlapping shifts after a partial import.

## Alternatives considered

- A PostgreSQL `btree_gist` exclusion constraint on (employer, employee, tsrange): rejected as the primary guard because the default build runs on H2, which has no such constraint, so the rule would not hold there.
- A content-level idempotency key now: deferred because it requires a stable upload business key and a deduplication store, while overlap rejection already prevents duplicate pay. Reconsider if partial re-sends become common.
- An application check without the employee row lock: rejected because the timeout-retry race lets two concurrent uploads both pass the check and insert duplicate shifts.

## Consequences

Rejecting new overlaps does not recover wages already overpaid before this change. The team can run this query to find existing overlapping shifts and reconcile the overpayments separately with Margaret:

```sql
SELECT a.employer_id, a.employee_id, a.id AS shift_a, b.id AS shift_b
FROM timesheets a
JOIN timesheets b
  ON a.employer_id = b.employer_id AND a.employee_id = b.employee_id
 AND a.id < b.id AND a.starts < b.ends AND b.starts < a.ends
ORDER BY a.employer_id, a.employee_id, a.id;
```

The employee row lock briefly serialises concurrent uploads for the same employee. This short contention is acceptable for payroll; the lock guards correctness, not throughput.
