package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PayCalendarRandomTest {
  private static final long SEED = 202010310027L;
  private static final int TRIALS = 20_000;
  private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

  @Test
  void generatedCalendarsAndShiftsMatchIndependentOracles() {
    System.out.printf("calendar seed=%d trials=%d%n", SEED, TRIALS);
    SplittableRandom random = new SplittableRandom(SEED);
    PayEngine engine = new PayEngine();
    for (int i = 0; i < TRIALS; i++) {
      int year = random.nextInt(2019, 2031);
      PayCalendar.Frequency frequency =
          PayCalendar.Frequency.values()[random.nextInt(PayCalendar.Frequency.values().length)];
      LocalDate anchor = LocalDate.of(2020, 1, 1).plusDays(random.nextInt(14));
      LocalDate day =
          LocalDate.ofYearDay(
              year, random.nextInt(1, LocalDate.of(year, 12, 31).getDayOfYear() + 1));
      if (random.nextInt(8) == 0) {
        day = transitionDay(year, random.nextBoolean());
      }
      ZonedDateTime start = ZonedDateTime.of(day, LocalTime.of(random.nextInt(24), 0), MELBOURNE);
      ZonedDateTime end = start.plusHours(random.nextInt(1, 13));
      String context =
          String.format(
              "seed=%s trial=%s frequency=%s start=%s end=%s", SEED, i, frequency, start, end);
      var periods = PayCalendar.periodsForYear(frequency, anchor, year);
      assertThat(periods).as(context).isNotEmpty();
      for (var period : periods) {
        assertThat(period.payDate().getYear()).as(context).isEqualTo(year);
        assertThat(period.payDate())
            .as(context)
            .isAfterOrEqualTo(period.startInclusive())
            .isBefore(period.endExclusive());
      }
      if (frequency == PayCalendar.Frequency.MONTHLY) {
        assertThat(periods).as(context).hasSize(12);
      } else {
        long days = frequency == PayCalendar.Frequency.WEEKLY ? 7 : 14;
        for (int j = 1; j < periods.size(); j++) {
          assertThat(
                  ChronoUnit.DAYS.between(periods.get(j - 1).payDate(), periods.get(j).payDate()))
              .as(context)
              .isEqualTo(days);
        }
      }

      long expectedMinutes = Duration.between(start.toInstant(), end.toInstant()).toMinutes();
      BigDecimal expectedHours =
          BigDecimal.valueOf(expectedMinutes)
              .divide(new BigDecimal("60"), 4, RoundingMode.HALF_EVEN);
      var result =
          engine.calculate(
              new BigDecimal("20"),
              LegacyPayEngine.Employment.PERMANENT,
              List.of(
                  new LegacyPayEngine.Shift(start.toLocalDateTime(), end.toLocalDateTime(), false)),
              BigDecimal.ZERO);
      assertThat(result.ordinaryHours().add(result.overtimeHours()))
          .as(context)
          .isEqualByComparingTo(expectedHours);
    }
  }

  private static LocalDate transitionDay(int year, boolean spring) {
    LocalDate first = LocalDate.of(year, spring ? 10 : 4, 1);
    return first.plusDays((7 - first.getDayOfWeek().getValue()) % 7);
  }
}
