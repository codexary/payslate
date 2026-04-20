package com.elsworne.payslate.engine;

import java.math.BigDecimal;
import java.util.Objects;

/** Payslate 7 year-to-date total. */
public class YtdAccumulator {
    private BigDecimal yearToDate = BigDecimal.ZERO;

    public BigDecimal add(BigDecimal gross, BigDecimal adjustment) {
        Objects.requireNonNull(gross);
        Objects.requireNonNull(adjustment);
        BigDecimal total = yearToDate;
        if (adjustment.signum() < 0) total = BigDecimal.ZERO;
        total = total.add(gross).add(adjustment);
        yearToDate = total;
        return total;
    }
}
