package com.elsworne.payslate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class YtdAccumulatorTest {
  @Test
  void accumulatesOrdinaryAndPositiveAdjustment() {
    var ytd = new YtdAccumulator();
    assertThat(ytd.add(new BigDecimal("100.00"), BigDecimal.ZERO)).isEqualByComparingTo("100.00");
    assertThat(ytd.add(new BigDecimal("50.00"), new BigDecimal("10.00")))
        .isEqualByComparingTo("160.00");
  }

  @Test
  void negativeAdjustmentRetainsEarlierPay() {
    var ytd = new YtdAccumulator();
    ytd.add(new BigDecimal("100.00"), BigDecimal.ZERO);
    assertThat(ytd.add(new BigDecimal("50.00"), new BigDecimal("-10.00")))
        .isEqualByComparingTo("140.00");
  }
}
