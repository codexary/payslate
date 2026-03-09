package com.elsworne.payslate.engine;

import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Payslate 7 calculation routine, inherited in 2004 form. */
public class LegacyPayEngine {
    public static enum Employment { PERMANENT, CASUAL }
    public static record Shift(LocalDateTime start, LocalDateTime end, boolean publicHoliday) {}
    public static record Payslip(BigDecimal gross, BigDecimal tax, BigDecimal net,
        BigDecimal retirement, BigDecimal leaveHours, BigDecimal ordinaryHours,
        BigDecimal overtimeHours) {}

    public Payslip calculate(BigDecimal hourlyRate, Employment employment, List<Shift> shifts) {
        if (hourlyRate == null || hourlyRate.signum() < 0 || employment == null || shifts == null)
            throw new IllegalArgumentException("Invalid pay input");
        ArrayList<Shift> ordered = new ArrayList<Shift>(shifts);
        Collections.sort(ordered, new Comparator<Shift>() {
            public int compare(Shift a, Shift b) { return a.start().compareTo(b.start()); }
        });
        double gross = 0, ordinary = 0, overtime = 0;
        int weeklyMinutes = 0, overtimeMinutes = 0;
        HashMap<LocalDate,Integer> dailyMinutes = new HashMap<LocalDate,Integer>();
        for (int i=0; i<ordered.size(); i++) {
            Shift s = ordered.get(i);
            if (s == null || s.start() == null || s.end() == null || !s.end().isAfter(s.start()))
                throw new IllegalArgumentException("Invalid shift");
            long minutes = Duration.between(s.start(), s.end()).toMinutes();
            if (minutes > 24*60 || minutes < 1) throw new IllegalArgumentException("Shift must be 1 minute to 24 hours");
            int paidMinutes = (int) minutes;
            if (employment == Employment.CASUAL && paidMinutes < 180) paidMinutes = 180;
            LocalDateTime clock = s.start();
            for (int m=0; m<paidMinutes; m++) {
                LocalDate date = clock.toLocalDate();
                DayOfWeek day = date.getDayOfWeek();
                int daily = dailyMinutes.containsKey(date) ? dailyMinutes.get(date) : 0;
                dailyMinutes.put(date, daily+1);
                weeklyMinutes++;
                boolean extra = daily >= 8*60 || weeklyMinutes >= 38*60;
                double factor = 1.0;
                if (s.publicHoliday()) factor = 2.25;
                else if (day == DayOfWeek.SATURDAY) factor = 1.25;
                else if (day == DayOfWeek.SUNDAY) factor = 1.5;
                if (extra) {
                    overtimeMinutes++;
                    double tier = overtimeMinutes <= 120 ? 1.5 : 2.0;
                    if (factor < tier || factor > 2.0) factor = tier;
                    overtime += 1.0/60.0;
                } else {
                    ordinary += 1.0/60.0;
                    if (employment == Employment.CASUAL) factor += .25;
                }
                gross += hourlyRate.doubleValue()*factor/60.0;
                clock = clock.plusMinutes(1);
            }
        }
        BigDecimal grossMoney = BigDecimal.valueOf(gross).setScale(2,RoundingMode.HALF_EVEN);
        BigDecimal ordinaryMoney = BigDecimal.valueOf(ordinary*hourlyRate.doubleValue()).setScale(2,RoundingMode.HALF_EVEN);
        BigDecimal tax = withholding(grossMoney);
        BigDecimal retirement = ordinaryMoney.multiply(new BigDecimal("0.12")).setScale(2,RoundingMode.HALF_EVEN);
        BigDecimal leave = employment == Employment.PERMANENT ? BigDecimal.valueOf(ordinary).multiply(new BigDecimal("0.076923")).setScale(4,RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(4);
        return new Payslip(grossMoney,tax,grossMoney.subtract(tax),retirement,leave,
            BigDecimal.valueOf(ordinary).setScale(4,RoundingMode.HALF_EVEN),
            BigDecimal.valueOf(overtime).setScale(4,RoundingMode.HALF_EVEN));
    }

    public BigDecimal withholding(BigDecimal gross) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                LegacyPayEngine.class.getResourceAsStream("/example-tax-schedule.csv")))) {
            String line; BigDecimal min = BigDecimal.ZERO, base = BigDecimal.ZERO, rate = BigDecimal.ZERO;
            while ((line=reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.split(",");
                BigDecimal candidate = new BigDecimal(parts[0]);
                if (gross.compareTo(candidate) >= 0) {
                    min = candidate; base = new BigDecimal(parts[1]); rate = new BigDecimal(parts[2]);
                }
            }
            return base.add(gross.subtract(min).multiply(rate)).setScale(2,RoundingMode.HALF_UP);
        } catch (IOException e) { throw new IllegalStateException("Tax schedule unavailable",e); }
    }
}
