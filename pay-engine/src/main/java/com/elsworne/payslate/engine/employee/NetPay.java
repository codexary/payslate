package com.elsworne.payslate.engine.employee;

import com.elsworne.payslate.engine.LegacyPayEngine;
import java.math.BigDecimal;

public final class NetPay {
  private final Withholding withholding;

  public NetPay() {
    this(new LegacyPayEngine()::withholding);
  }

  public NetPay(Withholding withholding) {
    this.withholding = withholding;
  }

  public BigDecimal net(EmployeePay pay, BigDecimal hours, BigDecimal adjustment) {
    BigDecimal gross = pay.gross(hours);
    BigDecimal tax = withholding.withholding(gross);
    return EmployeePay.cents(gross.add(adjustment).subtract(tax));
  }
}
