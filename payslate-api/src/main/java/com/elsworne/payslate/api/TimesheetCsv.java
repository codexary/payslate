package com.elsworne.payslate.api;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
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
    String first = text.split("\\R", 2)[0];
    char delimiter = first.contains("\t") ? '\t' : first.contains(";") ? ';' : ',';
    long line = 1;
    try (var parser =
        CSVFormat.RFC4180
            .builder()
            .setDelimiter(delimiter)
            .get()
            .parse(new LineEndingReader(new StringReader(text)))) {
      var records = parser.iterator();
      if (!records.hasNext()) throw new IllegalArgumentException("Missing header");
      var header = records.next();
      checkFields(header, line);
      Map<String, Integer> columns = new HashMap<>();
      for (int i = 0; i < header.size(); i++)
        columns.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
      int employee = column(columns, "employee", "employee name", "staff");
      int date = column(columns, "date", "shift date");
      int start = column(columns, "start", "start time");
      int end = column(columns, "end", "end time");
      int holiday = optionalColumn(columns, "public holiday", "public_holiday", "holiday");
      var rows = new ArrayList<Row>();
      while (true) {
        line = parser.getCurrentLineNumber() + 1;
        if (!records.hasNext()) break;
        var row = records.next();
        checkFields(row, line);
        if (row.size() == 1 && row.get(0).isBlank()) continue;
        if (row.size() != header.size())
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
    } catch (UncheckedIOException e) {
      throw new IllegalArgumentException("Line " + line + ": invalid quoted field", e);
    } catch (IOException | java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid CSV", e);
    }
  }

  private static void checkFields(org.apache.commons.csv.CSVRecord record, long line) {
    for (String field : record) {
      if (field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0)
        throw new IllegalArgumentException("Line " + line + ": quoted field contains a line break");
      if (field.indexOf('\uFEFF') >= 0)
        throw new IllegalArgumentException("Line " + line + ": unexpected BOM");
    }
  }

  private static final class LineEndingReader extends FilterReader {
    private long line = 1;
    private boolean carriageReturn;

    LineEndingReader(Reader reader) {
      super(reader);
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (carriageReturn && value != '\n')
        throw new IllegalArgumentException("Line " + line + ": bare carriage return");
      carriageReturn = value == '\r';
      if (value == '\n') line++;
      return value;
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
      if (length == 0) return 0;
      int count = 0;
      while (count < length) {
        int value = read();
        if (value < 0) return count == 0 ? -1 : count;
        buffer[offset + count++] = (char) value;
      }
      return count;
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
