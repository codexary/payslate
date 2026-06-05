package com.elsworne.payslate.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

class AbaPropertiesTest {
  @Property(tries = 100)
  void abaRoundTripPreservesPayments(@ForAll @LongRange(min = 1, max = 9999999) long cents) {
    var payment = new AbaFile.Payment("123-456", "12345678", "Alice Test", "WAGES", cents);
    var batch =
        new AbaFile.Batch(
            "TST",
            "ELSWORNE",
            "123456",
            "PAYROLL",
            LocalDate.of(2026, 2, 23),
            "123-456",
            "87654321",
            List.of(payment));
    assertThat(new AbaFile().parse(new AbaFile().write(batch)).payments()).containsExactly(payment);
  }
}
