package com.elsworne.payslate.engine;

import com.elsworne.payslate.engine.LegacyPayEngine.Employment;
import com.elsworne.payslate.engine.LegacyPayEngine.Shift;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;

/** Demonstration only: start,end,publicHoliday CSV with ISO local timestamps. */
public final class PayCli {
  private PayCli() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Usage: PayCli TIMESHEET.csv HOURLY_RATE PERMANENT|CASUAL");
    }
    var shifts = new ArrayList<Shift>();
    for (String line : Files.readAllLines(Path.of(args[0]))) {
      if (line.isBlank() || line.startsWith("start,")) continue;
      String[] fields = line.split(",", -1);
      if (fields.length != 3) throw new IllegalArgumentException("Expected three columns");
      shifts.add(
          new Shift(
              LocalDateTime.parse(fields[0]),
              LocalDateTime.parse(fields[1]),
              Boolean.parseBoolean(fields[2])));
    }
    var pay =
        new LegacyPayEngine()
            .calculate(new BigDecimal(args[1]), Employment.valueOf(args[2]), shifts);
    System.out.printf("Gross: %s%nTax: %s%nNet: %s%n", pay.gross(), pay.tax(), pay.net());
  }
}
