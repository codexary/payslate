English | [简体中文](0010-layered-payroll-integration.zh-CN.md)

# ADR 0010: Verify payroll seams and the roster contract

Ravi Menon — 2026-06-19

## Status

Accepted on 2026-06-19.

## Context

Payslate Online joins the tested pay engine and ABA writer to a Spring Boot API, Flyway and PostgreSQL. The first end-to-end slice served one employer and calculated every stored shift. Reviewing it, Margaret O'Connell asked whether a payroll officer can touch only their own employer's payroll and whether a pay run contains only that period's shifts. Staff JWTs now carry `employer_id` and the `PAYROLL_OFFICER` or `EMPLOYEE` role; employees may read only their own payslips. Pay runs carry a period, and calculation selects shifts by employer and period. Default `./mvnw -B -ntp clean verify` runs 80, 16 and 8 tests in the pay-engine, bank-file and API modules (104 in total); `-Pdocker verify` runs 80, 16 and 10 (106).

The first PostgreSQL integration test failed while `PayrollService.insert` read a generated key: the broad generated-key request returned every column of the new row, and `GeneratedKeyHolder.getKey()` expects one. The H2 system flow had passed. Requesting only `id` fixed it. The system test now checks a 36,000-cent public-holiday payment, the ABA trailer totals, period filtering and the run staying in `DRAFT` after an excessive deduction. A PostgreSQL integration test checks employer isolation and role boundaries.

The [rostering contract prototype](https://github.com/codexary/payslate-spike-contract) checks the Payslate consumer against a Pact mock server and the committed pact against a local stub provider. It has not contacted the live third-party provider.

## Decision

Keep fast CSV, rule, MockMvc and H2 feedback in the default build. Run the PostgreSQL generated-key and employer-isolation checks in the Docker profile, and run both profiles in CI. Integrate in dependency order: calculator and importer, the real database seam, then MVC and the upload-to-ABA flow. Keep Margaret's approval and reversal rules as named acceptance scenarios.

Scope every payroll data query to the JWT's `employer_id`. For an employee reading a payslip, also require the token's `employee_id`. Set the pay period when creating a run and select only shifts inside that period. Reject an excessive deduction for the entire calculation and leave the run in `DRAFT`.

Pin the roster request and the required response fields in a consumer pact before product integration. Claim cross-team compatibility only after the same pact is verified against a real provider release.

## Alternatives considered

- Use only the H2 whole flow: it passed while PostgreSQL generated-key handling failed.
- Put every test behind Docker: CSV, rule and MVC feedback can run without a container.
- Check only the staff role: a payroll officer could still guess another employer's record ID.
- Treat the local Pact stub as third-party verification: Payslate controls that stub.

## Consequences

The default and Docker CI jobs repeat some tests, but a database-specific failure has a narrow reproduction. The H2 system flow checks an authenticated upload-to-ABA path, its amount and its failure state. The PostgreSQL tests check generated keys and employer isolation, not the entire ABA path. Splitting cross-period shifts, stable IDs for employees with the same name and verification against the real third-party provider remain open decisions.
