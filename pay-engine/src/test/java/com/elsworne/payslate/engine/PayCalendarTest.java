package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayCalendarTest {
  @Test
  void januaryFirstAnchorGivesTwentySevenFortnightlyPayDatesIn2020() {
    assertThat(
            PayCalendar.periodsForYear(
                PayCalendar.Frequency.FORTNIGHTLY, LocalDate.of(2020, 1, 1), 2020))
        .hasSize(27);
  }

  @Test
  void february2020ContainsLeapDayAndExcludesMarchFirst() {
    var february =
        PayCalendar.periodsForYear(PayCalendar.Frequency.MONTHLY, LocalDate.of(2020, 1, 1), 2020)
            .get(1);
    assertThat(february.startInclusive()).isEqualTo(LocalDate.of(2020, 2, 1));
    assertThat(LocalDate.of(2020, 2, 29))
        .isAfterOrEqualTo(february.startInclusive())
        .isBefore(february.endExclusive());
    assertThat(february.endExclusive()).isEqualTo(LocalDate.of(2020, 3, 1));
  }
}
