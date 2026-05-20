package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

class CasualPayTest extends EmployeePayContractTest {
  @Override
  EmployeePay employee() {
    return new CasualPay(new BigDecimal("20"));
  }
}
