English | [简体中文](0008-payrun-transition-contract.zh-CN.md)

# ADR 0008: Make rejected pay-run events leave the run unchanged

Ravi Menon — 2026-05-22

## Status

Accepted on 2026-05-22.

## Context

`PayRun` starts in `DRAFT` and accepts `CALCULATE`, `APPROVE`, `PAY`, and `REVERSE` in that order. The initial implementation accepted a second `APPROVE` while already `APPROVED`: it incremented `approvalCount` and returned without changing state. `PayRunStateTest` exposed the path. The corrected implementation rejects it through the transition table before mutating either field. Duplicate payment authorization is a business risk; the object itself does not send payments.

The net-pay tests also show why the test boundary matters. A zero-returning `Withholding` mock could not distinguish original gross from adjusted gross. With the real example schedule, a $650.00 weekly salary and a −$200.00 correction exposed $435.00 instead of $450.00. `NetPay` now withholds on adjusted gross only when it is positive and skips withholding otherwise. The schedule itself rejects negative gross. The state and net-pay cases are described in [docs/test-design/payrun-states.md](../test-design/payrun-states.md).

## Decision

Keep one explicit transition table in [docs/test-design/payrun-states.md](../test-design/payrun-states.md). Test all four allowed transitions and all 16 rejected state/event pairs. For every rejection, assert both the exception and that `state` and `approvalCount` remain unchanged. Use a real `PayRun` in these tests and reach each starting state through its public operations. An invalid event must fail before any state or approval-count change.

Keep the subtype contract tests for `EmployeePay` separate; each concrete type's test class must inherit them and supply a real implementation. Use a `Withholding` double for controlled arithmetic tests, and retain `NetPayScheduleTest` with the real schedule to check which gross is taxed and how corrections affect net pay. A constant response alone cannot establish correct tax input.

## Alternatives considered

- **Test only the successful path:** it does not exercise repeat approval or a premature `PAY`.
- **Mock `PayRun` in the state tests:** the mock would answer according to its setup and would not expose the real `approvalCount` mutation.
- **Replace the product tests with generated histories:** this makes a small, reviewable business rule harder to inspect and does not remove the need to check rejected-event side effects.
- **Use only the zero-returning withholding mock:** both the faulty and corrected tax-input calculations satisfy the same net-pay assertions. The real-schedule cases distinguish them.

## Consequences

A new state or event requires updating the table and tests together. The rejection contract protects both the visible state and the approval count; checking only an exception or only the state is insufficient. The current object is in memory; persistence and concurrent approval protection remain work for the service that uses it.

The shared employee tests establish common behavior at their tested inputs, not every subtype's exact pay formula. The real-schedule net-pay tests complement the controlled-response tests; the synthetic example schedule does not establish compliance with an official tax table.
