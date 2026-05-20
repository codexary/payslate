package com.elsworne.payslate.engine.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

abstract class EmployeePayContractTest {
  abstract EmployeePay employee();

  @Test
  void grossIsNonnegativeAndHasCents() {
    var result = employee().gross(new BigDecimal("8"));
    assertThat(result.signum()).isGreaterThanOrEqualTo(0);
    assertThat(result.scale()).isEqualTo(2);
  }

  @Test
  void negativeHoursAreRejectedByEverySubtype() {
    assertThatThrownBy(() -> employee().gross(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
