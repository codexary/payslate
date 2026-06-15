package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UnitTimesheetCsvTest {
  @Test
  void parsesSpikePartitions() {
    for (String csv :
        new String[] {
          "employee,date,start,end\nAlice Test,2026-02-23,23:00,01:00\n",
          "\uFEFFstaff;shift date;start time;end time\r\nAlice Test;23/2/2026;11:00 PM;1:00 AM\r\n",
          "employee name\tdate\tstart\tend\nAlice Test\t2026-02-23\t23:00\t01:00\n",
          "employee,date,start,end\n\"Test, Alice\",2026-02-23,23:00,01:00\n"
        }) {
      var rows = TimesheetCsv.parse(csv);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).end()).isAfter(rows.get(0).start());
    }
  }

  @Test
  void parsesPublicHolidayColumn() {
    var rows =
        TimesheetCsv.parse(
            "employee,date,start,end,public holiday\nAlice Test,2026-02-23,09:00,17:00,yes\n");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).publicHoliday()).isTrue();
    assertThatThrownBy(
            () ->
                TimesheetCsv.parse(
                    "employee,date,start,end,public holiday\nAlice Test,2026-02-23,09:00,17:00,maybe\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid public holiday value");
  }

  @Test
  void rejectsMissingHeaderAndEmptyShift() {
    assertThatThrownBy(
            () -> TimesheetCsv.parse("person,date,start,end\nAlice,2026-02-23,09:00,10:00"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> TimesheetCsv.parse("employee,date,start,end\nAlice,2026-02-23,09:00,09:00"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
