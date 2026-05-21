package com.elsworne.payslate.engine.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.elsworne.payslate.engine.LegacyPayEngine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NetPayScheduleTest {
  private final NetPay netPay = new NetPay();
  private final SalariedPay salary = new SalariedPay(new BigDecimal("650.00"));

  @Test
  void salaryCorrectionUsesAdjustedGrossForWithholding() {
    var schedule = new LegacyPayEngine();
    assertThat(schedule.withholding(new BigDecimal("650.00"))).isEqualByComparingTo("15.00");
    assertThat(schedule.withholding(new BigDecimal("450.00"))).isEqualByComparingTo("0.00");

    var result = netPay.net(salary, new BigDecimal("38"), new BigDecimal("-200.00"));

    assertThat(result).isEqualByComparingTo("450.00");
  }

  @Test
  void correctionExceedingGrossWithholdsNothing() {
    var result = netPay.net(salary, new BigDecimal("38"), new BigDecimal("-700.00"));

    assertThat(result).isEqualByComparingTo("-50.00");
  }

  @Test
  void correctionEqualToGrossWithholdsNothing() {
    var result = netPay.net(salary, new BigDecimal("38"), new BigDecimal("-650.00"));

    assertThat(result).isEqualByComparingTo("0.00");
  }
}
