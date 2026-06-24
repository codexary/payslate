package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import com.elsworne.payslate.engine.LegacyPayEngine.Shift;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayEngineTimeZoneTest {
  @Test
  void configuredZoneControlsElapsedTime() {
    var result =
        calculate(
            new PayEngine(ZoneId.of("UTC")),
            Employment.PERMANENT,
            "2023-04-02T01:00",
            "2023-04-02T09:00");
    assertThat(hours(result)).isEqualTo("8.0000");
    assertThat(result.gross()).isEqualTo("240.00");
  }

  @Test
  void localEndpointsUseEarlierOverlapOffsetAndMoveGapsForward() {
    var engine = new PayEngine();
    assertThat(
            hours(calculate(engine, Employment.PERMANENT, "2023-04-02T02:00", "2023-04-02T03:00")))
        .isEqualTo("2.0000");
    assertThat(
            hours(calculate(engine, Employment.PERMANENT, "2023-10-01T02:00", "2023-10-01T04:00")))
        .isEqualTo("1.0000");
    assertThatThrownBy(
            () -> calculate(engine, Employment.PERMANENT, "2023-10-01T02:00", "2023-10-01T03:00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Shift must be 1 minute to 24 hours");
  }

  @Test
  void maximumDurationAndLocalMidnightFollowElapsedMinutes() {
    var engine = new PayEngine();
    assertThatThrownBy(
            () -> calculate(engine, Employment.PERMANENT, "2023-04-02T00:00", "2023-04-03T00:00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Shift must be 1 minute to 24 hours");
    var autumn = calculate(engine, Employment.PERMANENT, "2023-04-02T01:00", "2023-04-03T00:00");
    assertThat(hours(autumn)).isEqualTo("24.0000");
    assertThat(autumn.gross()).isEqualTo("860.00");
    var spring = calculate(engine, Employment.PERMANENT, "2023-10-01T01:00", "2023-10-02T01:00");
    assertThat(hours(spring)).isEqualTo("23.0000");
    assertThat(spring.gross()).isEqualTo("800.00");
  }

  @Test
  void casualMinimumAppliesAfterResolvingElapsedDuration() {
    var result =
        calculate(new PayEngine(), Employment.CASUAL, "2023-10-01T01:00", "2023-10-01T04:00");
    assertThat(hours(result)).isEqualTo("3.0000");
    assertThat(result.gross()).isEqualTo("105.00");
  }

  private static PayEngine.Result calculate(
      PayEngine engine, Employment employment, String start, String end) {
    return engine.calculate(
        new BigDecimal("20"),
        employment,
        List.of(new Shift(LocalDateTime.parse(start), LocalDateTime.parse(end), false)),
        BigDecimal.ZERO);
  }

  private static BigDecimal hours(PayEngine.Result result) {
    return result.ordinaryHours().add(result.overtimeHours());
  }
}
