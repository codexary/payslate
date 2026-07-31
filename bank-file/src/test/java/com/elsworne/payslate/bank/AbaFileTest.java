package com.elsworne.payslate.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AbaFileTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("accountTitles")
  void convertsAccountTitle(String name, String input, String expected) {
    assertThat(AbaFile.accountTitle(input)).isEqualTo(expected);
  }

  static Stream<Arguments> accountTitles() {
    return Stream.of(
        Arguments.of("accents", "Renée Dubois", "Renee Dubois"),
        Arguments.of("decomposed_accents", "Rene\u0301e Dubois", "Renee Dubois"),
        Arguments.of("unsupported_punctuation", "Lee & Sons Café", "Lee Sons Cafe"),
        Arguments.of("forty_characters", "A".repeat(40), "A".repeat(32)),
        Arguments.of("valid_name", "Anne-Marie O'Neil, Jr.", "Anne-Marie O'Neil, Jr."),
        Arguments.of("valid_digits", "Team 42", "Team 42"),
        Arguments.of("spaces", "  Lee   Sons  ", "Lee Sons"),
        Arguments.of("trim_after_cut", "A".repeat(31) + " B", "A".repeat(31)),
        Arguments.of("non_latin_only", "陈静", ""));
  }

  private static AbaFile.Batch batch(String bsb, long cents) {
    return new AbaFile.Batch(
        "CBA",
        "Payslate Test",
        "123456",
        "PAYROLL",
        LocalDate.of(2026, 2, 20),
        "000-000",
        "TEST123",
        List.of(new AbaFile.Payment(bsb, "TEST456", "Ava Test", "PAY-1", cents)));
  }

  static Stream<Arguments> amountBoundaries() {
    return Stream.of(
        Arguments.of("amount_below_minimum_0", 0L, false),
        Arguments.of("amount_on_minimum_1", 1L, true),
        Arguments.of("amount_inside_9999999998", 9_999_999_998L, true),
        Arguments.of("amount_on_maximum_9999999999", 9_999_999_999L, true),
        Arguments.of("amount_above_maximum_10000000000", 10_000_000_000L, false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("amountBoundaries")
  void amountBoundary(String name, long cents, boolean valid) {
    AbaFile aba = new AbaFile();
    if (valid) {
      String file = aba.write(batch("000-001", cents));
      assertThat(file.split("\r\n")).as(name).allSatisfy(line -> assertThat(line).hasSize(120));
      assertThat(file.split("\r\n")[1].substring(20, 30)).isEqualTo(String.format("%010d", cents));
      assertThat(aba.parse(file).payments().getFirst().cents()).isEqualTo(cents);
    } else {
      assertThatThrownBy(() -> aba.write(batch("000-001", cents)))
          .as(name)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  static Stream<Arguments> bsbBoundaries() {
    return Stream.of(
        Arguments.of("bsb_valid_000_000", "000-000", true),
        Arguments.of("bsb_valid_999_999", "999-999", true),
        Arguments.of("bsb_short_00_000", "00-000", false),
        Arguments.of("bsb_long_0000_000", "0000-000", false),
        Arguments.of("bsb_missing_hyphen", "000000", false),
        Arguments.of("bsb_nondigit", "00A-000", false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bsbBoundaries")
  void bsbBoundary(String name, String bsb, boolean valid) {
    AbaFile aba = new AbaFile();
    if (valid) {
      assertThat(aba.parse(aba.write(batch(bsb, 100))).payments().getFirst().bsb())
          .as(name)
          .isEqualTo(bsb);
    } else {
      assertThatThrownBy(() -> aba.write(batch(bsb, 100)))
          .as(name)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tamperedFiles")
  void rejectsTamperedRecord(String name, int line, int start, int end, String replacement) {
    AbaFile aba = new AbaFile();
    String[] records = aba.write(batch("000-001", 100)).split("\r\n");
    records[line] = records[line].substring(0, start) + replacement + records[line].substring(end);
    assertThatThrownBy(() -> aba.parse(String.join("\r\n", records) + "\r\n"))
        .as(name)
        .isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> tamperedFiles() {
    return Stream.of(
        Arguments.of("record_wrong_length", 1, 0, 1, ""),
        Arguments.of("header_invalid_date", 0, 74, 80, "999999"),
        Arguments.of("detail_invalid_amount_character", 1, 20, 21, "X"),
        Arguments.of("trailer_wrong_credit_total", 3, 30, 40, "0000000000"),
        Arguments.of("detail_invalid_bsb_separator", 1, 4, 5, "X"));
  }
}
