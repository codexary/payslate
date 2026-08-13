package com.elsworne.payslate.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Pins both pay engines to the withholding model proven in analysis/tax_schedule_check.py, at the
 * bracket edges taken from the schedule CSV and at the representative step points Z3 reported. Each
 * row's origin is recorded in the fixture header (withholding-boundaries.csv). Reviewed by Hannah
 * Schmidt.
 */
class WithholdingBoundaryTest {

  @Test
  void bothEnginesMatchTheModelAtTheBoundaries() throws Exception {
    var legacy = new com.elsworne.payslate.legacy.LegacyPayEngine();

    int checked = 0;
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                WithholdingBoundaryTest.class.getResourceAsStream(
                    "/withholding-boundaries.csv")))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#")) continue;
        String[] parts = line.split(",", 3);
        BigDecimal gross = new BigDecimal(parts[0].trim());
        BigDecimal expected = new BigDecimal(parts[1].trim());
        String origin = parts[2].trim();
        assertEquals(
            0,
            legacy.withholding(gross).compareTo(expected),
            "legacy engine at " + gross + " (" + origin + ")");
        assertEquals(
            0,
            PayEngine.withholding(gross).compareTo(expected),
            "pay engine at " + gross + " (" + origin + ")");
        checked++;
      }
    }
    assertEquals(8, checked, "expected eight boundary rows");
  }
}
