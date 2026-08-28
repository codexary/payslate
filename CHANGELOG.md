English | [简体中文](CHANGELOG.zh-CN.md)

# Changelog

All notable changes to Payslate are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

No changes yet.

## [1.0.0] - 2026-08-28

First released version. All data in this repository is synthetic.

### Added

- Hosted payroll API (Spring) with OIDC bearer authentication and officer and employee
  role checks, employer-scoped data access, and denied-request security events on a
  metrics dashboard.
- Pay engine: the refactored `PayEngine` computing pay, tax, retirement and leave from
  exact integer minutes, kept against the frozen 2004 engine by characterization,
  property and differential tests; the withholding schedule is proven with Z3.
- Timesheet CSV upload with strict UTF-8, size and row limits, line-numbered errors,
  and rejection of overlapping or duplicate shifts.
- ABA bank-file export, and the inherited local `aba-convert` converter with a required
  reconciling control trailer.
- Robustness testing before the external assessment: Jazzer on the parsers and the two
  pay engines, local AFL++ and hybrid (SymCC) fuzzing on the converter, KLEE on the
  batch reader, and scheduled fuzzing in CI.

### Fixed

- `aba-convert` now rejects a desktop export whose control trailer is missing or does not
  reconcile, instead of converting a silently truncated file as if it were complete.
- `aba-convert` now rejects a detail amount field that is not exactly ten digits, such as
  one padded with trailing spaces.

### Security

- `docs/security/assessment-findings.md` lists every security finding closed before the
  external assessment, with its fix commit and guarding regression.
- Advisory
  [PAYSLATE-2026-0001](docs/security/advisories/PAYSLATE-2026-0001.md) covers the two
  `aba-convert` memory-safety fixes (account-name stack buffer overflow, CVSS 7.8;
  batch-total integer overflow, CVSS 5.5).
