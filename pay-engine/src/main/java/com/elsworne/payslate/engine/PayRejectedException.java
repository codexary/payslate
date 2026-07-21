package com.elsworne.payslate.engine;

/** A pay rule rejected the input; the message is written for the payroll officer. */
public final class PayRejectedException extends IllegalArgumentException {
  public PayRejectedException(String message) {
    super(message);
  }
}
