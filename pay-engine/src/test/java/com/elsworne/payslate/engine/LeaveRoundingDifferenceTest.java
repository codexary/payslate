package com.elsworne.payslate.engine;

// Pins the known leave difference recorded in ADR 0018. On the roster the fuzzer found
// (kept as the enginesAgree seed), the paid amounts agree while the accrued leave
// differs by 0.0001 hour: the refactored engine, computing from exact minutes, reports
// 3.8462, and the frozen engine, accumulating in a double, reports 3.8461. If either
// rounding path changes, this test fails instead of the difference drifting unnoticed.

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaveRoundingDifferenceTest {
  private static final BigDecimal RATE = new BigDecimal("20");
  private static final LocalDate START = LocalDate.of(2026, 3, 2);
  private static final String SEED =
      "/com/elsworne/payslate/engine/DifferentialEngineFuzzTestInputs/enginesAgree/leave-rounding-divergence";

  @Test
  void leaveDiffersByOneUnitInTheFourthDecimal() throws IOException {
    byte[] data;
    try (InputStream in = getClass().getResourceAsStream(SEED)) {
      data = in.readAllBytes();
    }
    int days = Math.min(data.length / 2, 31);

    List<LegacyPayEngine.Shift> refactored = new ArrayList<>();
    List<com.elsworne.payslate.legacy.LegacyPayEngine.Shift> frozen = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      int hi = data[2 * i] & 0xff;
      int lo = data[2 * i + 1] & 0xff;
      int minutes = 1 + (((hi << 8) | lo) % (24 * 60));
      boolean holiday = (hi & 0x80) != 0;
      LocalDateTime start = START.plusDays(i).atTime(8, 0);
      LocalDateTime end = start.plusMinutes(minutes);
      refactored.add(new LegacyPayEngine.Shift(start, end, holiday));
      frozen.add(new com.elsworne.payslate.legacy.LegacyPayEngine.Shift(start, end, holiday));
    }

    var a =
        new PayEngine()
            .calculate(RATE, LegacyPayEngine.Employment.PERMANENT, refactored, BigDecimal.ZERO);
    var b =
        new com.elsworne.payslate.legacy.LegacyPayEngine()
            .calculate(
                RATE, com.elsworne.payslate.legacy.LegacyPayEngine.Employment.PERMANENT, frozen);

    // The money the migration must preserve is identical.
    assertThat(a.gross()).isEqualByComparingTo(b.gross());
    assertThat(a.net()).isEqualByComparingTo(b.net());
    // The displayed leave differs by exactly one unit in the fourth decimal.
    assertThat(a.leaveHours()).isEqualByComparingTo("3.8462");
    assertThat(b.leaveHours()).isEqualByComparingTo("3.8461");
  }
}
