package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import com.elsworne.payslate.engine.LegacyPayEngine.Shift;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PayBoundariesTest {
  static Stream<Arguments> hourBoundaries() {
    return Stream.of(
        Arguments.of("daily_below_7h59", 0, 479, Employment.PERMANENT, "159.67", "0.0000"),
        Arguments.of("daily_on_8h", 0, 480, Employment.PERMANENT, "160.00", "0.0000"),
        Arguments.of("daily_above_8h01", 0, 481, Employment.PERMANENT, "160.50", "0.0167"),
        Arguments.of("tier_below_9h59", 0, 599, Employment.PERMANENT, "219.50", "1.9833"),
        Arguments.of("tier_on_10h", 0, 600, Employment.PERMANENT, "220.00", "2.0000"),
        Arguments.of("tier_above_10h01", 0, 601, Employment.PERMANENT, "220.67", "2.0167"),
        Arguments.of("weekly_below_37h59", 32, 359, Employment.PERMANENT, "759.67", "0.0000"),
        Arguments.of("weekly_on_38h", 32, 360, Employment.PERMANENT, "760.00", "0.0000"),
        Arguments.of("weekly_above_38h01", 32, 361, Employment.PERMANENT, "760.50", "0.0167"),
        Arguments.of("casual_below_minimum_2h59", 0, 179, Employment.CASUAL, "75.00", "0.0000"),
        Arguments.of("casual_on_minimum_3h", 0, 180, Employment.CASUAL, "75.00", "0.0000"),
        Arguments.of("casual_above_minimum_3h01", 0, 181, Employment.CASUAL, "75.42", "0.0000"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("hourBoundaries")
  void hourBoundary(
      String name,
      int priorHours,
      int finalMinutes,
      Employment type,
      String gross,
      String overtime) {
    List<Shift> shifts = new ArrayList<>();
    for (int day = 0; day < priorHours / 8; day++) {
      LocalDateTime start = LocalDateTime.parse("2026-02-16T09:00").plusDays(day);
      shifts.add(new Shift(start, start.plusHours(8), false));
    }
    LocalDateTime start = LocalDateTime.parse("2026-02-20T09:00");
    shifts.add(new Shift(start, start.plusMinutes(finalMinutes), false));
    var result = new LegacyPayEngine().calculate(new BigDecimal("20"), type, shifts);
    assertThat(result.gross()).as(name).isEqualByComparingTo(gross);
    assertThat(result.overtimeHours()).as(name).isEqualByComparingTo(overtime);
  }

  static Stream<Arguments> taxBoundaries() {
    return Stream.of(
        Arguments.of("tax_below_500_49999", "499.99", "0.00"),
        Arguments.of("tax_on_500", "500.00", "0.00"),
        Arguments.of("tax_above_500_50001", "500.01", "0.00"),
        Arguments.of("tax_below_half_cent_500049", "500.049", "0.00"),
        Arguments.of("tax_half_up_50005", "500.05", "0.01"),
        Arguments.of("tax_above_half_cent_500051", "500.051", "0.01"),
        Arguments.of("tax_below_1000_99999", "999.99", "50.00"),
        Arguments.of("tax_on_1000", "1000.00", "50.00"),
        Arguments.of("tax_above_1000_100001", "1000.01", "50.00"),
        Arguments.of("tax_half_up_1000025", "1000.025", "50.01"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("taxBoundaries")
  void taxBoundary(String name, String gross, String expected) {
    assertThat(new LegacyPayEngine().withholding(new BigDecimal(gross)))
        .as(name)
        .isEqualByComparingTo(expected);
  }

  static Stream<Arguments> payRounding() {
    return Stream.of(
        Arguments.of("pay_below_half_cent_0004", "0.004", "0.00"),
        Arguments.of("pay_half_even_down_0005", "0.005", "0.00"),
        Arguments.of("pay_above_half_cent_0006", "0.006", "0.01"),
        Arguments.of("pay_half_even_up_0015", "0.015", "0.02"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("payRounding")
  void payRoundBoundary(String name, String hourlyRate, String gross) {
    var shift = LegacyPayEngineTest.shift("2026-02-16T10:00", "2026-02-16T11:00", false);
    assertThat(
            new LegacyPayEngine()
                .calculate(new BigDecimal(hourlyRate), Employment.PERMANENT, List.of(shift))
                .gross())
        .as(name)
        .isEqualByComparingTo(gross);
  }
}
