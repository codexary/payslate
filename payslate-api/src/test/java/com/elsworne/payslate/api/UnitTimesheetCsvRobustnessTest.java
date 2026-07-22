package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.LocalDate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnitTimesheetCsvRobustnessTest {
  // Stable message fragments: punctuation and the rest of each sentence are unrestricted.
  static final String LINE_TWO = "Line 2";
  static final String LINE_THREE = "Line 3";
  static final String QUOTED_LINE_BREAK = "quoted field contains a line break";
  static final String QUOTED_FIELD = "quoted field";
  static final String HEADER = "employee,date,start,end\n";
  static final String ROW = "Liam Walsh,2026-07-06,09:00,17:00\n";
  static final String QUOTED_ROWS =
      "\"employee\",\"date\",\"start\",\"end\"\n"
          + "\"Liam Walsh\",\"2026-07-06\",\"09:00\",\"17:00\"\n"
          + "\"Renée Dubois\",\"2026-07-07\",\"09:00\",\"17:00\"\n";

  @Test
  void acceptsFullyQuotedFieldsAcrossMultipleRows() {
    var rows = assertDoesNotThrow(() -> TimesheetCsv.parse(QUOTED_ROWS));
    assertThat(rows)
        .extracting(TimesheetCsv.Row::employee)
        .containsExactly("Liam Walsh", "Renée Dubois");
  }

  @Test
  void acceptsTheReducedCampaignInputWithQuotedNames() {
    var rows =
        assertDoesNotThrow(
            () ->
                TimesheetCsv.parse(
                    "\uFEFFEmployee Name,Shift Date,start,End Time\r\n"
                        + "\"Renée Dubois\",2026-07-08,11:00 PM,1:00 AM\r\n"
                        + "\"Walsh, Liam\",10/7/2026,23:00,17:00\r\n"));
    assertThat(rows)
        .extracting(TimesheetCsv.Row::employee)
        .containsExactly("Renée Dubois", "Walsh, Liam");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n"})
  void rejectsQuotedLineBreakAtTheRecordStart(String newline) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TimesheetCsv.parse(
                    HEADER + "\"Liam" + newline + "Walsh\",2026-07-06,09:00,17:00\n"));
    assertThat(error).hasMessageContaining(LINE_TWO).hasMessageContaining(QUOTED_LINE_BREAK);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"\"Liam Walsh,2026-07-06,09:00,17:00", "\"Liam Walsh\"x,2026-07-06,09:00,17:00"})
  void rejectsBrokenQuotingWithItsLine(String row) {
    var error =
        assertThrows(IllegalArgumentException.class, () -> TimesheetCsv.parse(HEADER + row));
    assertThat(error).hasMessageContaining(LINE_TWO).hasMessageContaining(QUOTED_FIELD);
  }

  @Test
  void stillRejectsBareCarriageReturns() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TimesheetCsv.parse((HEADER + ROW).replace('\n', '\r')));
  }

  @Test
  void rejectsTwentyThousandRowsWithAStrayQuoteWithinFiveSeconds() {
    String csv = HEADER + "Liam Walsh,2026-\"07-06,09:00,17:00\n" + ROW.repeat(19_999);
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> assertThrows(IllegalArgumentException.class, () -> TimesheetCsv.parse(csv)));
  }

  @ParameterizedTest(name = "rejects name containing control value {0}")
  @MethodSource("nameControls")
  void rejectsControlCharactersInNames(int control) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TimesheetCsv.parse(
                    HEADER + "Liam" + (char) control + " Walsh,2026-07-06,09:00,17:00\n"));
    assertThat(error).hasMessageContaining(LINE_TWO);
  }

  static IntStream nameControls() {
    // CR and LF have separate record/line-ending tests; tab is allowed as a delimiter only.
    return IntStream.concat(
        IntStream.range(0, 32).filter(c -> c != '\r' && c != '\n'), IntStream.of(0x7F));
  }

  @Test
  void rejectsOverlongNameWithItsLineWithoutEchoingIt() {
    String name = "x".repeat(101);
    var error =
        assertThrows(
            InvalidRequestException.class,
            () -> TimesheetCsv.parse(HEADER + name + ",2026-07-06,09:00,17:00\n"));
    assertThat(error).hasMessageContaining(LINE_TWO).hasMessageContaining("name");
    assertThat(error.getMessage()).doesNotContain(name);
  }

  @Test
  void acceptsAndTrimsOneHundredCharacterName() {
    String name = "x".repeat(100);
    assertThat(
            TimesheetCsv.parse(HEADER + "  " + name + "  ,2026-07-06,09:00,17:00\n")
                .getFirst()
                .employee())
        .isEqualTo(name);
  }

  @Test
  void acceptsTabsAsDelimiters() {
    assertThat(TimesheetCsv.parse((HEADER + ROW).replace(',', '\t'))).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"+999999999-07-20", "1999-12-31", "2100-01-01"})
  void rejectsYearsOutsideTheSupportedRange(String date) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> TimesheetCsv.parse(HEADER + "Mia Chen," + date + ",09:00,17:00\n"));
    assertThat(error).hasMessageContaining(LINE_TWO);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2000-01-01", "2099-12-31"})
  void acceptsBoundaryYears(String date) {
    var rows = TimesheetCsv.parse(HEADER + "Mia Chen," + date + ",09:00,17:00\n");
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().start().toLocalDate()).isEqualTo(LocalDate.parse(date));
  }

  @ParameterizedTest(name = "reports line for {0}")
  @MethodSource("invalidRows")
  void reportsThePhysicalLineOfEachInvalidRow(String reason, String row) {
    var error =
        assertThrows(IllegalArgumentException.class, () -> TimesheetCsv.parse(HEADER + ROW + row));
    assertThat(error).hasMessageContaining(LINE_THREE);
  }

  static Stream<Arguments> invalidRows() {
    return Stream.of(
        Arguments.of("impossible date", "Mia Chen,2026-02-30,09:00,17:00\n"),
        Arguments.of("invalid time", "Mia Chen,2026-07-07,noon,17:00\n"),
        Arguments.of("wrong column count", "Mia Chen,2026-07-07,09:00\n"),
        Arguments.of("empty name", ",2026-07-07,09:00,17:00\n"),
        Arguments.of("empty shift", "Mia Chen,2026-07-07,09:00,09:00\n"),
        Arguments.of("embedded BOM", "Mia\uFEFF Chen,2026-07-07,09:00,17:00\n"));
  }

  @Test
  void reportsTheLineOfAnInvalidHolidayValue() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TimesheetCsv.parse(
                    "employee,date,start,end,holiday\n"
                        + "Mia Chen,2026-07-07,09:00,17:00,maybe\n"));
    assertThat(error).hasMessageContaining(LINE_TWO);
  }

  @Test
  void ignoresAnExtraUnknownColumn() {
    var rows =
        TimesheetCsv.parse(
            "employee,date,start,end,notes\n" + "Mia Chen,2026-07-07,09:00,17:00,training\n");
    assertThat(rows)
        .containsExactly(
            TimesheetCsv.parse(HEADER + "Mia Chen,2026-07-07,09:00,17:00\n").getFirst());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "employee,date,date,start,end\nMia Chen,2026-07-07,2026-07-07,09:00,17:00\n",
        "employee,staff,date,start,end\nMia Chen,Mia Chen,2026-07-07,09:00,17:00\n"
      })
  void rejectsDuplicateKnownColumns(String csv) {
    var error = assertThrows(IllegalArgumentException.class, () -> TimesheetCsv.parse(csv));
    assertThat(error).hasMessageContaining("Line 1");
  }

  @Test
  void stillRejectsAMissingRequiredColumn() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TimesheetCsv.parse("employee,date,start\nMia Chen,2026-07-07,09:00\n"));
  }

  @Test
  void acceptsExactlyTenThousandDataRowsWithoutCountingBlankRows() {
    assertThat(TimesheetCsv.parse(HEADER + (ROW + "\n").repeat(10_000))).hasSize(10_000);
  }

  @Test
  void rejectsMoreThanTenThousandDataRows() {
    assertThrows(
        IllegalArgumentException.class, () -> TimesheetCsv.parse(HEADER + ROW.repeat(10_001)));
  }
}
