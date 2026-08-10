English | [简体中文](0015-require-a-control-trailer-on-desktop-exports.zh-CN.md)

# ADR 0015: Require a control trailer on desktop exports

Ravi Menon — 2026-08-10

## Status

Accepted on 2026-08-10.

## Context

`aba-convert` turns a desktop batch export into the bank's ABA layout. Preparing for the external assessment, Kwame found that an export truncated at a line boundary — a copy or transfer that stopped early — was converted without complaint: the records that survived produced a self-consistent ABA file, so one employee was silently left unpaid. A truncation in the middle of a record exited with an error but had already written the ABA header to standard output, leaving a partial file behind. The Java `AbaFile.parse` has long reconciled the record count and totals in its trailer; the inherited C converter never did.

## Decision

A desktop export must end with a control trailer line: a `T`, a 6-digit record count and a 10-digit total in cents, then nothing but the line ending. `aba-convert` reads the whole input once, keeps the parsed records in memory, and writes nothing unless the header is present, the record count equals the number of detail lines, the declared total equals the sum of the amounts, and no data follows the trailer. Any mismatch — including a missing trailer or trailing characters on it — exits with status 2 and no output.

An empty batch — a trailer declaring zero records and a zero total, with no detail lines — is rejected rather than converted. A bank file with no payments is meaningless and almost always means the export went wrong; Kwame confirmed the desktop product never emits one intentionally.

The control trailer is written by the desktop product, Payslate 7. Older desktop builds do not write it; their exports are now rejected with a message to re-export from a patched Payslate 7. The patch ships with the Payslate 7 maintenance release going out before the assessment; until an operator installs it, that site cannot convert new exports. Kwame owns the desktop release and its timing; Chloe's team has the message and the re-export steps for operators who hit the rejection.

## Alternatives considered

- Declare the record count and total in the header instead of a trailer: rejected. A header count would catch a missing record just as well, but the desktop product writes the batch as it goes, so the count and total are known only after the last record; the end of the file is where they naturally go. A trailer also matches the ABA format itself and what the Java `AbaFile.parse` already checks.
- Warn on a missing trailer but still convert: rejected because a silent short-payment is exactly the failure this change must stop; a warning on a batch job is easily missed.
- A per-line checksum: rejected as more than this format needs; a record count and a total catch truncation and tampering of amounts, and match what the Java side already checks.

## Consequences

Operators on an unpatched desktop build cannot convert until they upgrade; the rejection message and Chloe's steps make the upgrade path clear. A reconciling trailer does not authenticate the transfer to the bank — an attacker who can rewrite both the records and the trailer still passes — so authenticated transfer stays open in the threat model.
