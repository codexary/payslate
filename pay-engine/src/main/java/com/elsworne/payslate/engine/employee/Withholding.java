package com.elsworne.payslate.engine.employee;

import java.math.BigDecimal;

@FunctionalInterface
public interface Withholding {
  BigDecimal withholding(BigDecimal gross);
}
