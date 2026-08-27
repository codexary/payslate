English | [简体中文](SECURITY.zh-CN.md)

# Security policy

## Reporting a vulnerability

Email security@elsworne.example with a description, the affected component and
version, and the steps to reproduce. Please report privately and give us time to fix
the issue before any public disclosure. If you need to send sensitive details, ask in
the first message and we will provide a key.

Do not include real personal, payroll or bank data in a report. All data in this
repository is synthetic; reproduce with synthetic data only.

## What to expect

- We acknowledge a report within three business days.
- We triage and give an initial assessment, with a severity, within ten business days.
- We aim to release a fix for a confirmed high-severity issue within thirty days, and
  to coordinate a disclosure date with the reporter.
- We credit reporters who ask to be credited once a fix is released.

## Supported versions

We support the most recent released version of Payslate. The hosted service always
runs the latest release. The inherited desktop converter `aba-convert` is distributed
with the repository; sites that still run an older local build should rebuild from the
latest release, which carries the converter memory-safety fixes.

| Component | Supported |
| --- | --- |
| Hosted API and pay engine | Latest release |
| `aba-convert` converter | Latest release; older local builds should be rebuilt |
