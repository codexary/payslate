package com.elsworne.payslate.engine.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NetPayTest {
  static Stream<Arguments> payCases() {
    return Stream.of(
        Arguments.of("salary", new SalariedPay(new BigDecimal("650.00")), "38", "0.00", "650.00"),
        Arguments.of("hourly", new HourlyPay(new BigDecimal("20.00")), "8", "0.00", "160.00"),
        Arguments.of("casual", new CasualPay(new BigDecimal("20.00")), "8", "0.00", "200.00"),
        Arguments.of("bonus", new HourlyPay(new BigDecimal("20.00")), "8", "40.00", "200.00"),
        Arguments.of(
            "salary correction",
            new SalariedPay(new BigDecimal("650.00")),
            "38",
            "-200.00",
            "450.00"),
        Arguments.of(
            "full correction", new SalariedPay(new BigDecimal("650.00")), "38", "-650.00", "0.00"),
        Arguments.of(
            "excess correction",
            new SalariedPay(new BigDecimal("650.00")),
            "38",
            "-700.00",
            "-50.00"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("payCases")
  void netIncludesAdjustmentWhenNothingIsWithheld(
      String name, EmployeePay pay, String hours, String adjustment, String expected) {
    Withholding withholding = mock(Withholding.class);
    when(withholding.withholding(any(BigDecimal.class))).thenReturn(new BigDecimal("0.00"));

    var result =
        new NetPay(withholding).net(pay, new BigDecimal(hours), new BigDecimal(adjustment));

    assertThat(result).as(name).isEqualByComparingTo(expected);
    assertThat(result.scale()).isEqualTo(2);
  }
}
