package com.elsworne.payslate.engine;

// Differential fuzzing of the two engines. The frozen engine derives leave and the
// displayed hours from a double that accumulates 1.0/60.0 per minute; the refactored
// engine derives them from exact integer minutes. Both compute money from exact
// minutes, so any disagreement should surface only in the displayed hours or leave,
// never in the paid amounts. The input bytes become a month of shifts: two bytes make
// one day's minutes (1..1440, so a day can pass the 8-hour daily-overtime line and the
// month can pass the weekly-overtime line), the high bit flags a public holiday. The
// test compares the paid amounts the migration must preserve and the payslip
// invariants, not the displayed hours or leave: ADR 0018 keeps the frozen engine as an
// oracle for the money only, and LeaveRoundingDifferenceTest pins the 0.0001-hour gap.

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class DifferentialEngineFuzzTest {
  private static final BigDecimal RATE = new BigDecimal("20");
  private static final BigDecimal NO_DEDUCTIONS = BigDecimal.ZERO;
  private static final LocalDate START = LocalDate.of(2026, 3, 2);
  private static final int MAX_DAYS = 31;

  @FuzzTest(maxDuration = "2m")
  void enginesAgree(byte[] data) {
    int days = Math.min(data.length / 2, MAX_DAYS);
    if (days == 0) return;

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

    PayEngine.Result a;
    com.elsworne.payslate.legacy.LegacyPayEngine.Payslip b;
    try {
      a =
          new PayEngine()
              .calculate(RATE, LegacyPayEngine.Employment.PERMANENT, refactored, NO_DEDUCTIONS);
      b =
          new com.elsworne.payslate.legacy.LegacyPayEngine()
              .calculate(
                  RATE, com.elsworne.payslate.legacy.LegacyPayEngine.Employment.PERMANENT, frozen);
    } catch (IllegalArgumentException rejected) {
      return; // an invalid roster is not a finding
    }

    // Paid amounts: both engines compute these from exact integer minutes, so the
    // migration requires them to agree.
    assertThat(a.gross()).isEqualByComparingTo(b.gross());
    assertThat(a.tax()).isEqualByComparingTo(b.tax());
    assertThat(a.retirement()).isEqualByComparingTo(b.retirement());
    assertThat(a.net()).isEqualByComparingTo(b.net());
    // Payslip invariants on the authoritative engine.
    assertThat(a.net()).isEqualByComparingTo(a.gross().subtract(a.tax()).subtract(a.deductions()));
    assertThat(a.gross()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(a.tax()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }
}
