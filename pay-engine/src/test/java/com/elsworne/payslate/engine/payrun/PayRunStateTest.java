package com.elsworne.payslate.engine.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elsworne.payslate.engine.payrun.PayRun.Event;
import com.elsworne.payslate.engine.payrun.PayRun.State;
import org.junit.jupiter.api.Test;

class PayRunStateTest {
  @Test
  void everyValidTransition() {
    var run = new PayRun();
    assertThat(run.state()).isEqualTo(State.DRAFT);
    run.apply(Event.CALCULATE);
    assertThat(run.state()).isEqualTo(State.CALCULATED);
    run.apply(Event.APPROVE);
    assertThat(run.state()).isEqualTo(State.APPROVED);
    run.apply(Event.PAY);
    assertThat(run.state()).isEqualTo(State.PAID);
    run.apply(Event.REVERSE);
    assertThat(run.state()).isEqualTo(State.REVERSED);
  }

  @Test
  void everySneakPathLeavesStateAndApprovalCountUntouched() {
    Event[] events = Event.values();
    for (State state : State.values()) {
      for (Event event : events) {
        if (isValid(state, event)) continue;
        var run = at(state);
        int approvals = run.approvalCount();
        assertThatThrownBy(() -> run.apply(event))
            .as(state + " / " + event)
            .isInstanceOf(IllegalStateException.class);
        assertThat(run.state()).isEqualTo(state);
        assertThat(run.approvalCount()).isEqualTo(approvals);
      }
    }
  }

  private static boolean isValid(State state, Event event) {
    return switch (state) {
      case DRAFT -> event == Event.CALCULATE;
      case CALCULATED -> event == Event.APPROVE;
      case APPROVED -> event == Event.PAY;
      case PAID -> event == Event.REVERSE;
      case REVERSED -> false;
    };
  }

  private static PayRun at(State state) {
    var run = new PayRun();
    if (state.ordinal() >= State.CALCULATED.ordinal()) run.apply(Event.CALCULATE);
    if (state.ordinal() >= State.APPROVED.ordinal()) run.apply(Event.APPROVE);
    if (state.ordinal() >= State.PAID.ordinal()) run.apply(Event.PAY);
    if (state.ordinal() >= State.REVERSED.ordinal()) run.apply(Event.REVERSE);
    return run;
  }
}
