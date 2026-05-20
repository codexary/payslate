package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

class SalariedPayTest extends EmployeePayContractTest {
  @Override
  EmployeePay employee() {
    return new SalariedPay(new BigDecimal("1000"));
  }
}
