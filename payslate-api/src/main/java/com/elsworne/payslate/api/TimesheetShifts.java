package com.elsworne.payslate.api;

import java.util.List;
import java.util.OptionalLong;

final class TimesheetShifts {
  private TimesheetShifts() {}

  static OptionalLong firstInFileOverlapLine(List<TimesheetCsv.Row> rows) {
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      for (int j = 0; j < i; j++) {
        var earlier = rows.get(j);
        if (row.employee().equals(earlier.employee())
            && row.start().isBefore(earlier.end())
            && earlier.start().isBefore(row.end())) return OptionalLong.of(row.line());
      }
    }
    return OptionalLong.empty();
  }
}
