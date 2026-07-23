English | [简体中文](0003-timesheet-format-contract.zh-CN.md)

# ADR 0003: Set the timesheet import format contract

## Status

Accepted on 2026-03-20.

Partly amended by ADR 0012 (unknown extra columns are ignored).

## Context

The [timesheet format prototype](https://github.com/codexary/payslate-spike-timesheet-formats), explored on 2026-03-16 through 2026-03-19, separates format choices before the product importer is implemented. Its `TimesheetImporterTest` contains 22 test cases: 12 supported and 10 rejected representatives. The [findings](https://github.com/codexary/payslate-spike-timesheet-formats/blob/main/FINDINGS.md) distinguish behaviour exercised by those tests from the wider proposed contract. Synthetic text does not establish interoperability with real vendor exports, streaming behaviour or the product API.

## Decision

The product importer will support the following contract:

| Dimension | Supported |
| --- | --- |
| Delimiter | Comma, semicolon or tab, selected from the header and used consistently throughout the file |
| Required headers | `employee` / `employee name` / `staff`; `date` / `shift date`; `start` / `start time`; `end` / `end time`, case insensitive |
| Date and time | ISO `yyyy-MM-dd` or Australian `d/M/yyyy` dates with calendar validation; 24-hour `HH:mm` or 12-hour `h:mm AM/PM` times |
| Public holiday | Optional `public holiday` / `public_holiday` / `holiday`; case-insensitive `true` / `yes` / `1` and `false` / `no` / `0` / blank; absent column means false |
| Text and rows | Optional leading UTF-8 BOM; quoted delimiters and doubled quotes; blank rows ignored; LF and CRLF |
| Shift relation | One date plus start and end times; an earlier end means the following day; equal times are rejected as zero duration |

Reject mixed delimiters, unknown or missing headers, invalid dates or times, a BOM inside data, unclosed quotes, wrong column counts, empty employee names, bare CR, zero duration and invalid public-holiday values with clear errors. The public-holiday column is an explicit Boolean partition: both true and false must survive parsing, and absence defaults to false.

Shifts of 24 hours or more cannot be expressed in this format. Unequal times yield less than 24 hours, and equal times mean zero, not a full day. Supporting longer shifts would require a format change, such as an explicit end date; it is not a reachable “over 24 hours” rejection case in this prototype.

Use a maintained CSV parser and streaming input for the product importer. The prototype splits input into lines and rejects a newline inside a quoted field with `Unclosed quoted field`. The product importer must explicitly decide whether to support quoted newlines and test that decision before release.

The prototype parser is not the product parser. In particular, it searches for required header aliases but does not reject all unknown extra columns, and it has no general check for a BOM inside data. The product validation requirements above still need implementation and tests.

## Alternatives considered

- Accept only comma, ISO dates and 24-hour times. This excludes format classes represented by the supported prototype cases.
- Copy the prototype parser into the product. Its line splitting and small quote parser do not provide the required streaming and validation contract.
- Treat equal start/end times as a 24-hour shift. This would silently reinterpret the zero-duration rejection class and still could not express longer shifts.

## Consequences

Product tests must cover the supported and rejected classes at the importer boundary, including the parsed holiday flag and the chosen quoted-newline behaviour. The prototype's 22 tests establish format decisions only; they do not prove the product importer or real vendor exports work. Parser behaviour beyond these representatives must be checked against the contract, not assumed to be complete CSV validation.
