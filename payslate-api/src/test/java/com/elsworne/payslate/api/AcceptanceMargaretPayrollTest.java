package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elsworne.payslate.engine.payrun.PayRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("acceptance")
class AcceptanceMargaretPayrollTest {
  @Test
  void margaretRequiresApprovalBeforeAnyPayment() {
    var run = new PayRun();
    run.apply(PayRun.Event.CALCULATE);
    assertThatThrownBy(() -> run.apply(PayRun.Event.PAY)).isInstanceOf(IllegalStateException.class);
    run.apply(PayRun.Event.APPROVE);
    run.apply(PayRun.Event.PAY);
    assertThat(run.state()).isEqualTo(PayRun.State.PAID);
  }

  @Test
  void margaretRequiresReversalOnlyAfterPayment() {
    var run = new PayRun();
    assertThatThrownBy(() -> run.apply(PayRun.Event.REVERSE))
        .isInstanceOf(IllegalStateException.class);
  }
}
