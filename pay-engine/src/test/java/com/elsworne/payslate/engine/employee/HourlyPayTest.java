package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

class HourlyPayTest extends EmployeePayContractTest {
  @Override
  EmployeePay employee() {
    return new HourlyPay(new BigDecimal("20"));
  }
}
