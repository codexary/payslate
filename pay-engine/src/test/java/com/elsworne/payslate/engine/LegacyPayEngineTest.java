package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import com.elsworne.payslate.engine.LegacyPayEngine.Shift;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyPayEngineTest {
  private final LegacyPayEngine engine = new LegacyPayEngine();

  @Test
  void ordinaryWeekdayShift() {
    var result =
        engine.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(shift("2026-02-16T09:00", "2026-02-16T17:00", false)));
    assertThat(result.gross()).isEqualByComparingTo("160.00");
    assertThat(result.retirement()).isEqualByComparingTo("19.20");
    assertThat(result.leaveHours()).isPositive();
  }

  @Test
  void saturdayAndSundayPenalties() {
    var result =
        engine.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(
                shift("2026-02-14T10:00", "2026-02-14T11:00", false),
                shift("2026-02-15T10:00", "2026-02-15T11:00", false)));
    assertThat(result.gross()).isEqualByComparingTo("55.00");
  }

  @Test
  void publicHolidayPenalty() {
    var result =
        engine.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(shift("2026-02-16T10:00", "2026-02-16T11:00", true)));
    assertThat(result.gross()).isEqualByComparingTo("45.00");
  }

  static Shift shift(String start, String end, boolean holiday) {
    return new Shift(LocalDateTime.parse(start), LocalDateTime.parse(end), holiday);
  }
}
