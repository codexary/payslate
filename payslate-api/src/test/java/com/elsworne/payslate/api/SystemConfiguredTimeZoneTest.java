package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.elsworne.payslate.engine.payrun.PayRun;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "payslate.time-zone=UTC")
class SystemConfiguredTimeZoneTest {
  @Autowired PayrollService service;

  @Test
  void configuredZoneReachesTheServicePayEngine() {
    String employer = "zone-override";
    long employee =
        service.createEmployee(
            employer,
            "Zone Test",
            "PERMANENT",
            new BigDecimal("20"),
            "123-456",
            "12345678",
            BigDecimal.ZERO);
    service.upload(employer, "employee,date,start,end\nZone Test,2023-04-02,01:00,09:00\n");
    LocalDate day = LocalDate.of(2023, 4, 2);
    long run = service.createRun(employer, day, day);
    service.transition(employer, run, PayRun.Event.CALCULATE);
    assertThat((BigDecimal) service.payslip(employer, run, employee).get("gross"))
        .isEqualByComparingTo("240.00");
  }
}
