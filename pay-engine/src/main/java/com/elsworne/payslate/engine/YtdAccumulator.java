package com.elsworne.payslate.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Payslate 7 year-to-date total. */
public class YtdAccumulator {
  private BigDecimal yearToDate = BigDecimal.ZERO;

  public long roundedCents() {
    return yearToDate.movePointRight(2).longValueExact();
  }

  public BigDecimal add(BigDecimal gross, BigDecimal adjustment) {
    Objects.requireNonNull(gross);
    Objects.requireNonNull(adjustment);
    yearToDate = yearToDate.add(gross).add(adjustment).setScale(2, RoundingMode.HALF_EVEN);
    return yearToDate;
  }
}
