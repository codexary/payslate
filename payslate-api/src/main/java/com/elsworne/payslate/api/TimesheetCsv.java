package com.elsworne.payslate.api;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
  record Row(
      String employee, LocalDateTime start, LocalDateTime end, boolean publicHoliday, long line) {}

  static final int MAX_DATA_ROWS = 10_000;
  static final int MIN_YEAR = 2000;
  static final int MAX_YEAR = 2099;

  private static final DateTimeFormatter AU =
      DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TWELVE =
      DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  static List<Row> parse(String input) {
    if (input == null) throw new InvalidRequestException("Line 1: Empty CSV");
    return parse(new StringReader(input));
  }

  static List<Row> parse(InputStream input) {
    return parse(
        new InputStreamReader(
            input,
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)));
  }

  private static List<Row> parse(Reader input) {
    long line = 1;
    try (var reader = new BufferedReader(new LineEndingReader(input))) {
      reader.mark(1);
      if (reader.read() != '\uFEFF') reader.reset();
      reader.mark(1_048_576);
      String first = reader.readLine();
      reader.reset();
      reader.mark(0);
      if (first == null) throw new InvalidRequestException("Line 1: Missing header");
      char delimiter = first.contains("\t") ? '\t' : first.contains(";") ? ';' : ',';
      try (var parser = CSVFormat.RFC4180.builder().setDelimiter(delimiter).get().parse(reader)) {
        var records = parser.iterator();
        if (!records.hasNext()) throw new InvalidRequestException("Line 1: Missing header");
        var header = records.next();
        checkFields(header, line);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
          String column = knownColumn(header.get(i));
          if (column != null && columns.putIfAbsent(column, i) != null)
            throw new InvalidRequestException("Line 1: duplicate CSV header");
        }
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
            throw new InvalidRequestException("Line " + line + ": Wrong CSV column count");
          if (rows.size() == MAX_DATA_ROWS)
            throw new InvalidRequestException("Line " + line + ": too many data rows");
          String name;
          try {
            name = EmployeeName.normalize(row.get(employee));
          } catch (InvalidRequestException error) {
            throw new InvalidRequestException("Line " + line + ": " + error.getMessage(), error);
          }
          LocalDate day = date(row.get(date).trim(), line);
          if (day.getYear() < MIN_YEAR || day.getYear() > MAX_YEAR)
            throw new InvalidRequestException("Line " + line + ": shift year must be 2000–2099");
          LocalDateTime from = day.atTime(time(row.get(start).trim(), line));
          LocalDateTime to = day.atTime(time(row.get(end).trim(), line));
          if (to.isBefore(from)) to = to.plusDays(1);
          if (!to.isAfter(from))
            throw new InvalidRequestException("Line " + line + ": Empty shift");
          rows.add(new Row(name, from, to, holiday >= 0 && holiday(row.get(holiday), line), line));
        }
        if (rows.isEmpty()) throw new InvalidRequestException("Line " + line + ": No shifts");
        return List.copyOf(rows);
      }
    } catch (CharacterCodingException e) {
      throw new InvalidRequestException("File is not UTF-8", e);
    } catch (UncheckedIOException e) {
      if (e.getCause() instanceof CharacterCodingException)
        throw new InvalidRequestException("File is not UTF-8", e);
      throw new InvalidRequestException("Line " + line + ": invalid quoted field", e);
    } catch (IOException | java.time.DateTimeException e) {
      throw new InvalidRequestException("Line " + line + ": Invalid CSV", e);
    }
  }

  private static String knownColumn(String text) {
    return switch (text.trim().toLowerCase(Locale.ROOT)) {
      case "employee", "employee name", "staff" -> "employee";
      case "date", "shift date" -> "date";
      case "start", "start time" -> "start";
      case "end", "end time" -> "end";
      case "public holiday", "public_holiday", "holiday" -> "holiday";
      default -> null;
    };
  }

  private static void checkFields(org.apache.commons.csv.CSVRecord record, long line) {
    for (String field : record) {
      if (field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0)
        throw new InvalidRequestException("Line " + line + ": quoted field contains a line break");
      if (field.indexOf('\uFEFF') >= 0)
        throw new InvalidRequestException("Line " + line + ": unexpected BOM");
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
        throw new InvalidRequestException("Line " + line + ": bare carriage return");
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
    throw new InvalidRequestException("Line 1: Missing CSV header");
  }

  private static int optionalColumn(Map<String, Integer> columns, String... aliases) {
    for (String alias : aliases) if (columns.containsKey(alias)) return columns.get(alias);
    return -1;
  }

  private static boolean holiday(String text, long line) {
    return switch (text.trim().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0", "" -> false;
      default ->
          throw new InvalidRequestException("Line " + line + ": Invalid public holiday value");
    };
  }

  private static LocalDate date(String value, long line) {
    try {
      return LocalDate.parse(value);
    } catch (java.time.DateTimeException ignored) {
      try {
        return LocalDate.parse(value, AU);
      } catch (java.time.DateTimeException error) {
        throw new InvalidRequestException("Line " + line + ": invalid date", error);
      }
    }
  }

  private static LocalTime time(String value, long line) {
    try {
      return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
    } catch (java.time.DateTimeException ignored) {
      try {
        return LocalTime.parse(value.toUpperCase(Locale.ROOT), TWELVE);
      } catch (java.time.DateTimeException error) {
        throw new InvalidRequestException("Line " + line + ": invalid time", error);
      }
    }
  }
}
