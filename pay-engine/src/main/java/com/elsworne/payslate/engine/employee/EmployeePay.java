package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;
import java.math.RoundingMode;

public sealed interface EmployeePay permits SalariedPay, HourlyPay, CasualPay {
  BigDecimal gross(BigDecimal hours);

  static BigDecimal hours(BigDecimal hours) {
    if (hours == null || hours.signum() < 0) throw new IllegalArgumentException("hours");
    return hours;
  }

  static BigDecimal cents(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_EVEN);
  }
}
