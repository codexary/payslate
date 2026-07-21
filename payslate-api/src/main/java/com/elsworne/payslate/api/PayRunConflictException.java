package com.elsworne.payslate.api;

final class PayRunConflictException extends IllegalStateException {
  PayRunConflictException() {
    super("Action conflicts with the pay-run state.");
  }
}
