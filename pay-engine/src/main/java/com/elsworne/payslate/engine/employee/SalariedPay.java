package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

public record SalariedPay(BigDecimal weeklySalary) implements EmployeePay {
  public SalariedPay {
    if (weeklySalary == null || weeklySalary.signum() < 0)
      throw new IllegalArgumentException("salary");
  }

  @Override
  public BigDecimal gross(BigDecimal hours) {
    EmployeePay.hours(hours);
    return EmployeePay.cents(weeklySalary);
  }
}
