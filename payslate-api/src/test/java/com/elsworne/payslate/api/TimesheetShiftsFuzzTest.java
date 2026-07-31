package com.elsworne.payslate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

class TimesheetShiftsFuzzTest {
  @FuzzTest(maxDuration = "2m")
  void overlapFinderMatchesPairwise(FuzzedDataProvider data) {
    int n = data.consumeInt(0, 40);
    List<TimesheetCsv.Row> rows = new ArrayList<>(n);
    var base = LocalDate.of(2026, 2, 23);
    for (int i = 0; i < n; i++) {
      String employee = "E" + data.consumeInt(0, 3);
      var start = base.atStartOfDay().plusMinutes(data.consumeInt(0, 2000));
      var end = start.plusMinutes(data.consumeInt(1, 600));
      rows.add(new TimesheetCsv.Row(employee, start, end, false, i + 2));
    }

    OptionalLong expected = OptionalLong.empty();
    for (int i = 0; i < rows.size() && expected.isEmpty(); i++) {
      var a = rows.get(i);
      for (int j = 0; j < i; j++) {
        var b = rows.get(j);
        if (a.employee().equals(b.employee())
            && !(a.end().compareTo(b.start()) <= 0 || b.end().compareTo(a.start()) <= 0)) {
          expected = OptionalLong.of(rows.get(i).line());
          break;
        }
      }
    }

    OptionalLong actual = TimesheetShifts.firstInFileOverlapLine(rows);
    assertEquals(expected, actual, () -> "Overlap finder mismatch for rows: " + rows);
  }
}
