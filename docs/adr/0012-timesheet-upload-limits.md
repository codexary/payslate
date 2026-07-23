English | [简体中文](0012-timesheet-upload-limits.zh-CN.md)

# ADR 0012: Bound timesheet uploads and retain the robustness campaign

Ravi Menon — 2026-07-23

## Status

Accepted on 2026-07-23.

## Context

Support tickets described a fully quoted rostering export being rejected, an unclosed quote returning 500 after a long wait, and a Windows-1252 file reporting an unknown employee after its name was silently changed during decoding. None of the errors identified the failing line.

The manual HTTP robustness campaign with seed `20260717` sent 1 500 candidates to the running service. It rejected 211 valid files and returned 60 server errors; the slowest response took 29.09 s. The 60 errors had two causes: malformed quoting escaped the CSV error handler, and NUL reached PostgreSQL. Replay found 55 inputs with odd quotes, four with both NUL and odd quotes, and one with NUL alone. The old regular-expression pre-check also grew roughly quadratically: stray-quote files of 1 000 / 3 000 / 10 000 / 20 000 lines took 0.12 / 0.91 / 10.0 / 40.8 s. A byte limit alone did not prevent a long-running request.

The campaign groups findings by public response, so it did not identify NUL as a separate class; boundary-value review isolated it and found expanded years that parsed but exceeded the database's timestamp range. The Windows-1252 support ticket exposed silent character replacement: an ordinary 400 for an unknown employee did not violate the campaign's checks. These results describe one generated sample and its checks, not customer failure probabilities or complete input coverage.

## Decision

Accept UTF-8 only, with an optional leading BOM. Make the framework's existing 1 MB per-file default an explicit product limit; configure the multipart request limit to 1 MB too. Stream through a strict decoder and the CSV parser, replacing the regular-expression pre-check with linear parsing and record validation. Keep at most 10 000 data rows per file. An employer with 300 staff over a fortnight has about 6 000 shifts, so 10 000 leaves headroom while bounding one request's database work.

Accept shift years 2000–2099. This covers the desktop product's historical records, whose oldest entries are from the 2000s; the [inherited-engine note](../../pay-engine/LEGACY.md) dates its calculation routine to 2004. The range stays far inside what PostgreSQL `timestamp` can store. Reject quoted line breaks and report the record's start line.

Ignore unknown extra columns. This partly amends [ADR 0003](0003-timesheet-format-contract.md): rostering exports often include notes or break minutes that Payslate does not use. Missing required columns, duplicated known columns (including aliases) and rows whose width differs from the header remain rejected.

Apply the same employee-name rule to uploads and `POST /v1/employees`: 1–100 characters after trimming, with no control characters, including tab. Check controls before trimming so whitespace removal cannot hide them. Accept hourly rates from 0 to 9999.99 and deductions from 0 to 999999.99, both with at most two decimals.

Header and row errors return 400, identify the line and never echo the row. Reject a file that is not UTF-8 as a whole. Oversized multipart uploads return 413. Unexpected failures return a generic 500 problem without internal details.

Keep the [HTTP campaign](../../robustness/README.md) as a manual tool: it needs the whole Compose stack and minutes of run time. Turn its minimised findings, and boundaries found by review and support, into JUnit regression tests that run in CI. Repeat the campaign against the running service after fixes; the regression tests preserve known cases between manual runs.

## Alternatives considered

- Keep the regular-expression pre-check with a time limit: this retains valid-file rejections and backtracking, and adds cancellation handling instead of removing the cause.
- Accept any encoding with detection: a wrong guess can silently change an employee name; an explicit UTF-8 contract gives a reliable rejection.
- Rely on 1 MB without a row limit: small rows can still cause excessive database work in one request.
- Reject unknown columns as ADR 0003 specified: this would reject useful rostering exports for columns the service does not need.

## Consequences

A URI that is invalid at the protocol level (for example an encoded NUL, `%00`) is rejected by the servlet container before the application runs; that 400 is Tomcat’s minimal HTML page (no server version and no echo of the URI), not problem details, and the campaign’s event target reports it as a known `problem-format` finding.

The limits bound parsing and per-upload database work, and line-numbered errors make correction practical without reflecting payroll rows into error responses. Customers must convert other encodings to UTF-8 and split larger files. The campaign still cannot detect every misleading rejection or pay error; its checks and generated input classes need review alongside the regression tests.

Duplicate uploads and overlapping shifts for the same employee remain accepted. Uploading the same file twice pays its shifts twice. This is an open pay-correctness defect reported to the product owner, Margaret O'Connell, rather than a parsing problem; this decision does not address it.
