package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    var legacy = new com.elsworne.payslate.legacy.LegacyPayEngine();
    int legacyDisagreements = 0;
    int legacyRejections = 0;
    int[] frequencyTrials = new int[PayCalendar.Frequency.values().length];
    int[] frequencyDisagreements = new int[frequencyTrials.length];
    int transitionTrials = 0;
    int transitionDisagreements = 0;
    int offsetCrossings = 0;
    int disagreementCrossings = 0;
    int csvPolicyCases = 0;
    int csvPolicyRejections = 0;
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
      frequencyTrials[frequency.ordinal()]++;
      boolean transitionStart =
          day.equals(transitionDay(year, true)) || day.equals(transitionDay(year, false));
      if (transitionStart) transitionTrials++;
      boolean crossesOffset = !start.getOffset().equals(end.getOffset());
      if (crossesOffset) offsetCrossings++;
      boolean disagrees;
      try {
        var inherited =
            legacy.calculate(
                new BigDecimal("20"),
                com.elsworne.payslate.legacy.LegacyPayEngine.Employment.PERMANENT,
                List.of(
                    new com.elsworne.payslate.legacy.LegacyPayEngine.Shift(
                        start.toLocalDateTime(), end.toLocalDateTime(), false)));
        BigDecimal inheritedHours = inherited.ordinaryHours().add(inherited.overtimeHours());
        disagrees =
            inheritedHours
                    .multiply(new BigDecimal("60"))
                    .compareTo(BigDecimal.valueOf(expectedMinutes))
                != 0;
      } catch (IllegalArgumentException exception) {
        // The raw elapsed shift is positive, but CSV can collapse its endpoints to one time.
        assertThat(start.toLocalDateTime()).as(context).isEqualTo(end.toLocalDateTime());
        assertThat(exception).as(context).hasMessage("Invalid shift");
        legacyRejections++;
        disagrees = true;
      }
      if (disagrees) {
        legacyDisagreements++;
        frequencyDisagreements[frequency.ordinal()]++;
        if (transitionStart) transitionDisagreements++;
        if (crossesOffset) disagreementCrossings++;
      }
      // CSV cannot preserve a later overlap offset. Check the specified resolution policy
      // separately, without changing the random draws or claiming a raw-instant match.
      var csvStart = start.toLocalDateTime().atZone(MELBOURNE);
      var csvEnd = end.toLocalDateTime().atZone(MELBOURNE);
      if (!csvStart.toInstant().equals(start.toInstant())
          || !csvEnd.toInstant().equals(end.toInstant())) {
        csvPolicyCases++;
        expectedMinutes = Duration.between(csvStart.toInstant(), csvEnd.toInstant()).toMinutes();
      }
      var shifts =
          List.of(new LegacyPayEngine.Shift(start.toLocalDateTime(), end.toLocalDateTime(), false));
      if (expectedMinutes < 1) {
        csvPolicyRejections++;
        assertThatThrownBy(
                () ->
                    engine.calculate(
                        new BigDecimal("20"),
                        LegacyPayEngine.Employment.PERMANENT,
                        shifts,
                        BigDecimal.ZERO))
            .as(context)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Shift must be 1 minute to 24 hours");
        continue;
      }
      BigDecimal expectedHours =
          BigDecimal.valueOf(expectedMinutes)
              .divide(new BigDecimal("60"), 4, RoundingMode.HALF_EVEN);
      var result =
          engine.calculate(
              new BigDecimal("20"), LegacyPayEngine.Employment.PERMANENT, shifts, BigDecimal.ZERO);
      assertThat(result.ordinaryHours().add(result.overtimeHours()))
          .as(context)
          .isEqualByComparingTo(expectedHours);
    }
    System.out.printf(
        "current raw-instant matches=%d/%d csv-policy cases=%d (paid=%d rejected=%d)%n",
        TRIALS - csvPolicyCases,
        TRIALS,
        csvPolicyCases,
        csvPolicyCases - csvPolicyRejections,
        csvPolicyRejections);
    System.out.printf(
        "legacy raw-oracle disagreements=%d/%d (paid-hours=%d rejected=%d)%n",
        legacyDisagreements, TRIALS, legacyDisagreements - legacyRejections, legacyRejections);
    for (var frequency : PayCalendar.Frequency.values()) {
      System.out.printf(
          "%s legacy disagreements=%d/%d%n",
          frequency,
          frequencyDisagreements[frequency.ordinal()],
          frequencyTrials[frequency.ordinal()]);
    }
    System.out.printf(
        "transition-day legacy disagreements=%d/%d other-day legacy disagreements=%d/%d%n",
        transitionDisagreements,
        transitionTrials,
        legacyDisagreements - transitionDisagreements,
        TRIALS - transitionTrials);
    System.out.printf(
        "offset crossings=%d/%d legacy disagreement crossings=%d/%d%n",
        offsetCrossings, TRIALS, disagreementCrossings, legacyDisagreements);
    assertThat(legacyDisagreements).isPositive();
    assertThat(disagreementCrossings).isEqualTo(legacyDisagreements);
    assertThat(offsetCrossings).isEqualTo(legacyDisagreements);
  }

  @Test
  void formulaAgreesWithSeededSimulation() {
    double p = 0.01;
    double confidence = 0.95;
    int needed = (int) Math.ceil(Math.log1p(-confidence) / Math.log1p(-p));
    double formula = 1 - Math.pow(1 - p, needed);
    SplittableRandom random = new SplittableRandom(SEED + 1);
    int detected = 0;
    int campaigns = 10_000;
    for (int campaign = 0; campaign < campaigns; campaign++) {
      boolean found = false;
      for (int trial = 0; trial < needed; trial++) {
        if (random.nextDouble() < p) found = true;
      }
      if (found) detected++;
    }
    double observed = (double) detected / campaigns;
    System.out.printf(
        "simulation seed=%d p=%.4f confidence=%.2f n=%d formula=%.6f detected=%d/%d observed=%.4f%n",
        SEED + 1, p, confidence, needed, formula, detected, campaigns, observed);
    assertThat(needed).isEqualTo(299);
    assertThat(observed).isCloseTo(formula, org.assertj.core.data.Offset.offset(0.015));
  }

  private static LocalDate transitionDay(int year, boolean spring) {
    LocalDate first = LocalDate.of(year, spring ? 10 : 4, 1);
    return first.plusDays((7 - first.getDayOfWeek().getValue()) % 7);
  }
}
