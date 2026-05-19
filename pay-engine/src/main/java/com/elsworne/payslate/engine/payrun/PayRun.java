package com.elsworne.payslate.engine.payrun;

import java.util.Objects;

/** In-memory payroll run; persistence is supplied by the API. */
public final class PayRun {
  public enum State {
    DRAFT,
    CALCULATED,
    APPROVED,
    PAID,
    REVERSED
  }

  public enum Event {
    CALCULATE,
    APPROVE,
    PAY,
    REVERSE
  }

  private State state = State.DRAFT;
  private int approvalCount;

  public State state() {
    return state;
  }

  public int approvalCount() {
    return approvalCount;
  }

  public void apply(Event event) {
    Objects.requireNonNull(event, "event");
    State next =
        switch (state) {
          case DRAFT -> event == Event.CALCULATE ? State.CALCULATED : null;
          case CALCULATED -> event == Event.APPROVE ? State.APPROVED : null;
          case APPROVED -> event == Event.PAY ? State.PAID : null;
          case PAID -> event == Event.REVERSE ? State.REVERSED : null;
          case REVERSED -> null;
        };
    if (next == null) throw new IllegalStateException("Cannot " + event + " from " + state);
    if (event == Event.APPROVE) approvalCount++;
    state = next;
  }
}
