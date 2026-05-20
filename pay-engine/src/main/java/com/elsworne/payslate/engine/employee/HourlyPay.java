package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

public record HourlyPay(BigDecimal rate) implements EmployeePay {
  public HourlyPay {
    if (rate == null || rate.signum() < 0) throw new IllegalArgumentException("rate");
  }

  @Override
  public BigDecimal gross(BigDecimal hours) {
    return EmployeePay.cents(rate.multiply(EmployeePay.hours(hours)));
  }
}
