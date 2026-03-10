package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CasualPartitionsTest {
  static Stream<Arguments> validPartitions() {
    return Stream.of(
        Arguments.of(
            "casual_weekday_below_minimum",
            Employment.CASUAL,
            "2026-02-16T10:00",
            "2026-02-16T11:00",
            false,
            "75.00",
            false),
        Arguments.of(
            "casual_weekday_at_minimum",
            Employment.CASUAL,
            "2026-02-16T10:00",
            "2026-02-16T13:00",
            false,
            "75.00",
            false),
        Arguments.of(
            "casual_weekday_above_minimum",
            Employment.CASUAL,
            "2026-02-16T10:00",
            "2026-02-16T14:00",
            false,
            "100.00",
            false),
        Arguments.of(
            "casual_saturday_ordinary",
            Employment.CASUAL,
            "2026-02-14T10:00",
            "2026-02-14T13:00",
            false,
            "90.00",
            false),
        Arguments.of(
            "casual_sunday_ordinary",
            Employment.CASUAL,
            "2026-02-15T10:00",
            "2026-02-15T13:00",
            false,
            "105.00",
            false),
        Arguments.of(
            "casual_public_holiday_interaction",
            Employment.CASUAL,
            "2026-02-16T10:00",
            "2026-02-16T13:00",
            true,
            "150.00",
            false),
        Arguments.of(
            "permanent_weekday_no_minimum",
            Employment.PERMANENT,
            "2026-02-16T10:00",
            "2026-02-16T11:00",
            false,
            "20.00",
            true),
        Arguments.of(
            "casual_overtime_no_loading",
            Employment.CASUAL,
            "2026-02-16T09:00",
            "2026-02-16T18:00",
            false,
            "230.00",
            false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validPartitions")
  void validPartition(
      String name,
      Employment type,
      String start,
      String end,
      boolean holiday,
      String gross,
      boolean leave) {
    var pay =
        new LegacyPayEngine()
            .calculate(
                new BigDecimal("20"),
                type,
                List.of(LegacyPayEngineTest.shift(start, end, holiday)));
    assertThat(pay.gross()).as(name).isEqualByComparingTo(gross);
    assertThat(pay.leaveHours().signum() > 0).as(name).isEqualTo(leave);
  }

  static Stream<Arguments> invalidPartitions() {
    return Stream.of(
        Arguments.of(
            "invalid_zero_duration", Employment.CASUAL, "2026-02-16T10:00", "2026-02-16T10:00"),
        Arguments.of(
            "invalid_negative_duration", Employment.CASUAL, "2026-02-16T11:00", "2026-02-16T10:00"),
        Arguments.of("invalid_null_employment", null, "2026-02-16T10:00", "2026-02-16T11:00"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPartitions")
  void invalidPartition(String name, Employment type, String start, String end) {
    assertThatThrownBy(
            () ->
                new LegacyPayEngine()
                    .calculate(
                        new BigDecimal("20"),
                        type,
                        List.of(LegacyPayEngineTest.shift(start, end, false))))
        .as(name)
        .isInstanceOf(IllegalArgumentException.class);
  }
}
