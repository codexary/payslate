package com.elsworne.payslate.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates pay for elapsed time in a configured zone, retaining local dates for pay rules.
 *
 * <p>Local shift endpoints follow {@link java.time.LocalDateTime#atZone(ZoneId)}: an overlap
 * selects the earlier offset, and a time in a gap moves forward by the gap length. Local times
 * alone cannot identify the later occurrence of an overlapping time.
 */
public final class PayEngine {
  public record Result(
      BigDecimal gross,
      BigDecimal tax,
      BigDecimal deductions,
      BigDecimal net,
      BigDecimal retirement,
      BigDecimal leaveHours,
      BigDecimal ordinaryHours,
      BigDecimal overtimeHours) {}

  private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

  private final ZoneId zone;

  /** Uses Australia/Melbourne for local shift times. */
  public PayEngine() {
    this(ZoneId.of("Australia/Melbourne"));
  }

  /** Uses the supplied zone to resolve local shift endpoints and apply local calendar rules. */
  public PayEngine(ZoneId zone) {
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  public Result calculate(
      BigDecimal hourlyRate,
      LegacyPayEngine.Employment employment,
      List<LegacyPayEngine.Shift> shifts,
      BigDecimal deductions) {
    return calculate("unknown employee", hourlyRate, employment, shifts, deductions);
  }

  public Result calculate(
      String employeeName,
      BigDecimal hourlyRate,
      LegacyPayEngine.Employment employment,
      List<LegacyPayEngine.Shift> shifts,
      BigDecimal deductions) {
    if (employeeName == null
        || employeeName.isBlank()
        || hourlyRate == null
        || hourlyRate.signum() < 0
        || employment == null
        || shifts == null
        || deductions == null
        || deductions.signum() < 0) throw new IllegalArgumentException("Invalid pay input");
    for (var shift : shifts) paidMinutes(shift, employment);
    var ordered =
        shifts.stream().sorted(Comparator.comparing(LegacyPayEngine.Shift::start)).toList();
    Map<LocalDate, Integer> daily = new HashMap<>();
    Map<LocalDate, Integer> weekly = new HashMap<>();
    Map<LocalDate, Integer> weeklyOvertime = new HashMap<>();
    int overtime = 0, ordinary = 0;
    BigDecimal weighted = BigDecimal.ZERO;
    for (var shift : ordered) {
      int minutes = paidMinutes(shift, employment);
      ZonedDateTime clock = shift.start().atZone(zone);
      for (int m = 0; m < minutes; m++, clock = clock.plusMinutes(1)) {
        LocalDate date = clock.toLocalDate();
        int dayMinutes = daily.getOrDefault(date, 0);
        daily.put(date, dayMinutes + 1);
        LocalDate week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekMinutes = weekly.merge(week, 1, Integer::sum);
        boolean extra = dayMinutes >= 480 || weekMinutes > 2280;
        int overtimeTier = 0;
        if (extra) {
          overtime++;
          overtimeTier = weeklyOvertime.merge(week, 1, Integer::sum);
        } else ordinary++;
        weighted =
            weighted.add(factor(shift, clock.getDayOfWeek(), employment, extra, overtimeTier));
      }
    }
    BigDecimal gross =
        hourlyRate.multiply(weighted).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_EVEN);
    BigDecimal ordinaryMoney =
        hourlyRate
            .multiply(BigDecimal.valueOf(ordinary))
            .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_EVEN);
    BigDecimal tax = withholding(gross);
    BigDecimal available = gross.subtract(tax);
    if (deductions.compareTo(available) > 0)
      throw new IllegalArgumentException(
          "Deductions exceed available pay for employee " + employeeName);
    BigDecimal retirement =
        ordinaryMoney.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal leave =
        employment == LegacyPayEngine.Employment.PERMANENT
            ? BigDecimal.valueOf(ordinary)
                .divide(MINUTES_PER_HOUR, 10, RoundingMode.HALF_EVEN)
                .multiply(new BigDecimal("0.076923"))
                .setScale(4, RoundingMode.HALF_EVEN)
            : BigDecimal.ZERO.setScale(4);
    return new Result(
        gross,
        tax,
        deductions,
        gross.subtract(tax).subtract(deductions),
        retirement,
        leave,
        BigDecimal.valueOf(ordinary).divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_EVEN),
        BigDecimal.valueOf(overtime).divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_EVEN));
  }

  private static BigDecimal withholding(BigDecimal gross) {
    try (var input = PayEngine.class.getResourceAsStream("/example-tax-schedule.csv");
        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input))) {
      BigDecimal minimum = BigDecimal.ZERO, base = BigDecimal.ZERO, rate = BigDecimal.ZERO;
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#")) continue;
        String[] parts = line.split(",");
        BigDecimal candidate = new BigDecimal(parts[0]);
        if (gross.compareTo(candidate) >= 0) {
          minimum = candidate;
          base = new BigDecimal(parts[1]);
          rate = new BigDecimal(parts[2]);
        }
      }
      return base.add(gross.subtract(minimum).multiply(rate)).setScale(2, RoundingMode.HALF_UP);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Tax schedule unavailable", e);
    }
  }

  private int paidMinutes(LegacyPayEngine.Shift shift, LegacyPayEngine.Employment employment) {
    if (shift == null || shift.start() == null || shift.end() == null)
      throw new IllegalArgumentException("Invalid shift");
    long minutes =
        Duration.between(
                shift.start().atZone(zone).toInstant(), shift.end().atZone(zone).toInstant())
            .toMinutes();
    if (minutes < 1 || minutes > 1440)
      throw new IllegalArgumentException("Shift must be 1 minute to 24 hours");
    return (int)
        (employment == LegacyPayEngine.Employment.CASUAL ? Math.max(minutes, 180) : minutes);
  }

  private static BigDecimal factor(
      LegacyPayEngine.Shift shift,
      DayOfWeek day,
      LegacyPayEngine.Employment employment,
      boolean extra,
      int overtime) {
    BigDecimal dayFactor =
        shift.publicHoliday()
            ? new BigDecimal("2.25")
            : day == DayOfWeek.SATURDAY
                ? new BigDecimal("1.25")
                : day == DayOfWeek.SUNDAY ? new BigDecimal("1.50") : BigDecimal.ONE;
    if (extra)
      return dayFactor.max(overtime <= 120 ? new BigDecimal("1.50") : new BigDecimal("2.00"));
    return employment == LegacyPayEngine.Employment.CASUAL
        ? dayFactor.add(new BigDecimal("0.25"))
        : dayFactor;
  }
}
