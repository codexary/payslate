package com.elsworne.payslate.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pay dates and their calendar periods. */
public final class PayCalendar {
  public enum Frequency {
    WEEKLY,
    FORTNIGHTLY,
    MONTHLY
  }

  public record Period(LocalDate startInclusive, LocalDate endExclusive, LocalDate payDate) {}

  private PayCalendar() {}

  /** Returns periods whose pay dates fall in the given calendar year. */
  public static List<Period> periodsForYear(
      Frequency frequency, LocalDate anchorPayDate, int year) {
    if (frequency == null || anchorPayDate == null || year < 1 || year > 9999) {
      throw new IllegalArgumentException("Invalid calendar input");
    }
    List<Period> periods = new ArrayList<>();
    LocalDate first = LocalDate.of(year, 1, 1);
    LocalDate last = LocalDate.of(year, 12, 31);
    if (frequency == Frequency.MONTHLY) {
      for (int month = 1; month <= 12; month++) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);
        periods.add(new Period(start, end, end.minusDays(1)));
      }
    } else {
      int days = frequency == Frequency.WEEKLY ? 7 : 14;
      long offset =
          Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(anchorPayDate, first), days);
      LocalDate payDate = offset == 0 ? first : first.plusDays(days - offset);
      while (!payDate.isAfter(last)) {
        periods.add(new Period(payDate.minusDays(days - 1), payDate.plusDays(1), payDate));
        payDate = payDate.plusDays(days);
      }
    }
    return List.copyOf(periods);
  }
}
