package com.elsworne.payslate.api;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;

final class TimesheetCsv {
  record Row(String employee, LocalDateTime start, LocalDateTime end, boolean publicHoliday) {}

  private static final DateTimeFormatter AU =
      DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TWELVE =
      DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  static List<Row> parse(String input) {
    if (input == null || input.isBlank()) throw new IllegalArgumentException("Empty CSV");
    String text = input.startsWith("\uFEFF") ? input.substring(1) : input;
    if (text.indexOf('\uFEFF') >= 0
        || text.matches("(?s).*\\r(?!\\n).*")
        || text.matches("(?s).*\"[^\"]*\\n[^\"]*\".*"))
      throw new IllegalArgumentException("Unsupported CSV line ending or embedded newline");
    String first = text.split("\\R", 2)[0];
    char delimiter = first.contains("\t") ? '\t' : first.contains(";") ? ';' : ',';
    try (var parser =
        CSVFormat.RFC4180.builder().setDelimiter(delimiter).get().parse(new StringReader(text))) {
      var records = parser.getRecords();
      if (records.isEmpty()) throw new IllegalArgumentException("Missing header");
      Map<String, Integer> columns = new HashMap<>();
      for (int i = 0; i < records.get(0).size(); i++)
        columns.put(records.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
      int employee = column(columns, "employee", "employee name", "staff");
      int date = column(columns, "date", "shift date");
      int start = column(columns, "start", "start time");
      int end = column(columns, "end", "end time");
      int holiday = optionalColumn(columns, "public holiday", "public_holiday", "holiday");
      var rows = new ArrayList<Row>();
      for (int i = 1; i < records.size(); i++) {
        var row = records.get(i);
        if (row.size() == 1 && row.get(0).isBlank()) continue;
        if (row.size() != records.get(0).size())
          throw new IllegalArgumentException("Wrong CSV column count");
        String name = row.get(employee).trim();
        if (name.isBlank()) throw new IllegalArgumentException("Empty employee");
        LocalDate day = date(row.get(date).trim());
        LocalDateTime from = day.atTime(time(row.get(start).trim()));
        LocalDateTime to = day.atTime(time(row.get(end).trim()));
        if (to.isBefore(from)) to = to.plusDays(1);
        if (!to.isAfter(from)) throw new IllegalArgumentException("Empty shift");
        rows.add(new Row(name, from, to, holiday >= 0 && holiday(row.get(holiday))));
      }
      if (rows.isEmpty()) throw new IllegalArgumentException("No shifts");
      return List.copyOf(rows);
    } catch (IOException | java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid CSV", e);
    }
  }

  private static int column(Map<String, Integer> columns, String... aliases) {
    for (String alias : aliases) if (columns.containsKey(alias)) return columns.get(alias);
    throw new IllegalArgumentException("Missing CSV header");
  }

  private static int optionalColumn(Map<String, Integer> columns, String... aliases) {
    for (String alias : aliases) if (columns.containsKey(alias)) return columns.get(alias);
    return -1;
  }

  private static boolean holiday(String text) {
    return switch (text.trim().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0", "" -> false;
      default -> throw new IllegalArgumentException("Invalid public holiday value");
    };
  }

  private static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (java.time.DateTimeException ignored) {
      return LocalDate.parse(value, AU);
    }
  }

  private static LocalTime time(String value) {
    try {
      return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
    } catch (java.time.DateTimeException ignored) {
      return LocalTime.parse(value.toUpperCase(Locale.ROOT), TWELVE);
    }
  }
}
