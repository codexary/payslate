package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import com.elsworne.payslate.engine.LegacyPayEngine.Shift;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class PayEnginePropertiesTest {
  private final PayEngine current = new PayEngine();
  private final com.elsworne.payslate.legacy.LegacyPayEngine old =
      new com.elsworne.payslate.legacy.LegacyPayEngine();

  @Property(tries = 100)
  void parallelRunOracle(
      @ForAll @IntRange(min = 1, max = 500) int rate,
      @ForAll @IntRange(min = 1, max = 600) int minutes,
      @ForAll boolean casual,
      @ForAll boolean holiday,
      @ForAll @IntRange(min = 1, max = 7) int day) {
    var start = LocalDateTime.of(2026, 2, 21, 8, 0).plusDays(day);
    var shifts = List.of(new Shift(start, start.plusMinutes(minutes), holiday));
    var employment = casual ? Employment.CASUAL : Employment.PERMANENT;
    var a = current.calculate(BigDecimal.valueOf(rate), employment, shifts, BigDecimal.ZERO);
    var b =
        old.calculate(
            BigDecimal.valueOf(rate),
            casual
                ? com.elsworne.payslate.legacy.LegacyPayEngine.Employment.CASUAL
                : com.elsworne.payslate.legacy.LegacyPayEngine.Employment.PERMANENT,
            List.of(
                new com.elsworne.payslate.legacy.LegacyPayEngine.Shift(
                    start, start.plusMinutes(minutes), holiday)));
    assertThat(a.gross()).isEqualByComparingTo(b.gross());
    assertThat(a.tax()).isEqualByComparingTo(b.tax());
    assertThat(a.retirement()).isEqualByComparingTo(b.retirement());
    assertThat(a.leaveHours()).isEqualByComparingTo(b.leaveHours());
  }

  @Property(generation = GenerationMode.RANDOMIZED)
  void netEqualsGrossMinusTaxMinusDeductions(@ForAll @IntRange(min = 0, max = 100) int deductions) {
    var start = LocalDateTime.of(2026, 2, 23, 8, 0);
    var result =
        current.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(new Shift(start, start.plusHours(1), false)),
            BigDecimal.valueOf(deductions));
    assertThat(result.net())
        .isEqualByComparingTo(result.gross().subtract(result.tax()).subtract(result.deductions()));
  }

  @Test
  void refactoredEngineRejectsNullShiftBeforeSorting() {
    assertThatThrownBy(
            () ->
                current.calculate(
                    "Alice Test",
                    new BigDecimal("20"),
                    Employment.PERMANENT,
                    java.util.Arrays.asList((Shift) null),
                    BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid shift");
  }

  @Test
  void refactoredEngineResetsWeeklyOvertimeAcrossSundayMidnight() {
    List<Shift> shifts = new ArrayList<>();
    for (int day = 0; day < 5; day++) {
      var start = LocalDateTime.of(2026, 2, 16, 9, 0).plusDays(day);
      shifts.add(new Shift(start, start.plusHours(day == 4 ? 6 : 8), false));
    }
    shifts.add(
        new Shift(
            LocalDateTime.of(2026, 2, 22, 23, 30), LocalDateTime.of(2026, 2, 23, 0, 30), false));
    var result =
        current.calculate(
            "Alice Test", new BigDecimal("20"), Employment.PERMANENT, shifts, BigDecimal.ZERO);
    assertThat(result.gross()).isEqualByComparingTo("785.00");
    assertThat(result.overtimeHours()).isEqualByComparingTo("0.5000");
  }

  @Property
  void moreHoursNeverLowerGross(@ForAll @IntRange(min = 1, max = 600) int minutes) {
    var start = LocalDateTime.of(2026, 2, 23, 8, 0);
    var shorter =
        current.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(new Shift(start, start.plusMinutes(minutes), false)),
            BigDecimal.ZERO);
    var longer =
        current.calculate(
            new BigDecimal("20"),
            Employment.PERMANENT,
            List.of(new Shift(start, start.plusMinutes(minutes + 1), false)),
            BigDecimal.ZERO);
    assertThat(longer.gross()).isGreaterThanOrEqualTo(shorter.gross());
  }

  @Property
  void taxIsNeverNegative(@ForAll @IntRange(min = 1, max = 1000) int rate) {
    var start = LocalDateTime.of(2026, 2, 23, 8, 0);
    var result =
        current.calculate(
            BigDecimal.valueOf(rate),
            Employment.PERMANENT,
            List.of(new Shift(start, start.plusHours(1), false)),
            BigDecimal.ZERO);
    assertThat(result.tax().signum()).isGreaterThanOrEqualTo(0);
  }
}
