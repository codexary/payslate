package com.elsworne.payslate.bank;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Credit batch with a balancing debit entry, using 120-character Direct Entry records. */
public final class AbaFile {
  private static final long MAX_CENTS = 9_999_999_999L;
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("ddMMuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  public record Payment(String bsb, String account, String name, String reference, long cents) {}

  public record Batch(
      String institution,
      String userName,
      String userId,
      String description,
      LocalDate date,
      String traceBsb,
      String traceAccount,
      List<Payment> payments) {}

  public static String accountTitle(String name) {
    // Banks credit by BSB and account number; the title is only for reconciliation.
    String title =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^A-Za-z0-9 ,.'-]", " ")
            .replaceAll(" +", " ")
            .trim();
    return title.substring(0, Math.min(title.length(), 32)).trim();
  }

  public String write(Batch batch) {
    if (batch == null || batch.payments() == null || batch.payments().isEmpty())
      throw new IllegalArgumentException("At least one payment is required");
    require(batch.institution(), "[A-Z]{3}", "institution");
    require(batch.userId(), "[0-9]{6}", "user ID");
    requireBsb(batch.traceBsb());
    String traceAccount = account(batch.traceAccount());
    var lines = new ArrayList<String>();
    lines.add(
        "0"
            + " ".repeat(17)
            + "01"
            + batch.institution()
            + " ".repeat(7)
            + left(batch.userName(), 26)
            + batch.userId()
            + left(batch.description(), 12)
            + batch.date().format(DATE)
            + " ".repeat(40));
    long total = 0;
    for (Payment payment : batch.payments()) {
      requireBsb(payment.bsb());
      total = Math.addExact(total, amount(payment.cents()));
      if (total > MAX_CENTS) throw new IllegalArgumentException("Total exceeds ten-digit amount");
      lines.add(detail(payment, "53", batch.traceBsb(), traceAccount, batch.userName()));
    }
    lines.add(
        detail(
            new Payment(batch.traceBsb(), traceAccount, batch.userName(), "BATCH DEBIT", total),
            "13",
            batch.traceBsb(),
            traceAccount,
            batch.userName()));
    lines.add(
        "7999-999"
            + " ".repeat(12)
            + digits(0, 10)
            + digits(total, 10)
            + digits(total, 10)
            + " ".repeat(24)
            + digits(lines.size() - 1, 6)
            + " ".repeat(40));
    return String.join("\r\n", lines) + "\r\n";
  }

  public Batch parse(String text) {
    if (text == null || !text.endsWith("\r\n"))
      throw new IllegalArgumentException("ABA records require CRLF");
    String[] lines = text.substring(0, text.length() - 2).split("\r\n", -1);
    if (lines.length < 4) throw new IllegalArgumentException("Missing records");
    for (String line : lines)
      if (line.length() != 120) throw new IllegalArgumentException("Record must be 120 characters");
    String header = lines[0], trailer = lines[lines.length - 1];
    if (header.charAt(0) != '0'
        || trailer.charAt(0) != '7'
        || !header.substring(1, 18).isBlank()
        || !header.substring(18, 20).equals("01")
        || !header.substring(23, 30).isBlank()
        || !header.substring(80).isBlank()
        || !trailer.substring(1, 8).equals("999-999")
        || !trailer.substring(8, 20).isBlank()
        || !trailer.substring(50, 74).isBlank()
        || !trailer.substring(80).isBlank())
      throw new IllegalArgumentException("Invalid ABA header or trailer");
    require(header.substring(20, 23), "[A-Z]{3}", "institution");
    require(header.substring(56, 62), "[0-9]{6}", "user ID");
    if (header.substring(30, 56).isBlank() || header.substring(62, 74).isBlank())
      throw new IllegalArgumentException("Empty ABA header field");
    LocalDate date = LocalDate.parse(header.substring(74, 80), DATE);
    var payments = new ArrayList<Payment>();
    long credit = 0, debit = 0;
    String traceBsb = null, traceAccount = null;
    for (int i = 1; i < lines.length - 1; i++) {
      String line = lines[i];
      if (line.charAt(0) != '1') throw new IllegalArgumentException("Invalid detail record");
      String bsb = line.substring(1, 8), account = line.substring(8, 17).trim();
      requireBsb(bsb);
      if (!line.substring(8, 17).equals(account(account)) || line.substring(30, 62).isBlank())
        throw new IllegalArgumentException("Invalid detail account or name");
      if (line.charAt(17) != ' ' || !line.substring(112, 120).equals("00000000"))
        throw new IllegalArgumentException("Unsupported indicator or withholding");
      String code = line.substring(18, 20);
      long cents = parseDigits(line.substring(20, 30), 10);
      amount(cents);
      String tb = line.substring(80, 87), ta = line.substring(87, 96).trim();
      requireBsb(tb);
      if (!line.substring(87, 96).equals(account(ta)) || line.substring(96, 112).isBlank())
        throw new IllegalArgumentException("Invalid trace account or remitter");
      if (traceBsb == null) {
        traceBsb = tb;
        traceAccount = ta;
      } else if (!traceBsb.equals(tb) || !traceAccount.equals(ta))
        throw new IllegalArgumentException("Inconsistent trace account");
      if (code.equals("53")) {
        // Inherited path: the desktop product marked reversed payments with this
        // reference prefix and never sent them to the bank.
        String reference = line.substring(62, 80);
        if (reference.startsWith("PS7REVERSAL"))
          throw new IllegalStateException("Desktop reversal reference");
        credit = Math.addExact(credit, cents);
        payments.add(
            new Payment(
                bsb, account, line.substring(30, 62).trim(), line.substring(62, 80).trim(), cents));
      } else if (code.equals("13")) {
        debit = Math.addExact(debit, cents);
        if (i != lines.length - 2 || !bsb.equals(tb) || !account.equals(ta))
          throw new IllegalArgumentException("Invalid balancing debit");
      } else throw new IllegalArgumentException("Unsupported transaction code");
    }
    if (payments.isEmpty()
        || credit != debit
        || parseDigits(trailer.substring(20, 30), 10) != 0
        || parseDigits(trailer.substring(30, 40), 10) != credit
        || parseDigits(trailer.substring(40, 50), 10) != debit
        || parseDigits(trailer.substring(74, 80), 6) != lines.length - 2)
      throw new IllegalArgumentException("ABA totals do not reconcile");
    return new Batch(
        header.substring(20, 23),
        header.substring(30, 56).trim(),
        header.substring(56, 62),
        header.substring(62, 74).trim(),
        date,
        traceBsb,
        traceAccount,
        List.copyOf(payments));
  }

  private static String detail(Payment p, String code, String tb, String ta, String remitter) {
    return "1"
        + p.bsb()
        + account(p.account())
        + " "
        + code
        + digits(amount(p.cents()), 10)
        + left(p.name(), 32)
        + left(p.reference(), 18)
        + tb
        + account(ta)
        + left(remitter, 16)
        + "00000000";
  }

  private static String left(String value, int width) {
    if (value == null
        || value.isBlank()
        || value.length() > width
        || !value.matches("[A-Za-z0-9 ,.'-]+"))
      throw new IllegalArgumentException("Invalid text field");
    return value + " ".repeat(width - value.length());
  }

  private static String account(String value) {
    if (value == null
        || value.isBlank()
        || value.length() > 9
        || !value.matches("[A-Za-z0-9 -]+")
        || value.matches("0+")) throw new IllegalArgumentException("Invalid account");
    return " ".repeat(9 - value.length()) + value;
  }

  private static void requireBsb(String value) {
    require(value, "[0-9]{3}-[0-9]{3}", "BSB");
  }

  private static void require(String value, String regex, String field) {
    if (value == null || !value.matches(regex))
      throw new IllegalArgumentException("Invalid " + field);
  }

  private static long amount(long cents) {
    if (cents < 1 || cents > MAX_CENTS)
      throw new IllegalArgumentException("Amount must fit ten digits of cents");
    return cents;
  }

  private static String digits(long value, int width) {
    if (value < 0 || Long.toString(value).length() > width)
      throw new IllegalArgumentException("Numeric field overflow");
    return String.format(Locale.ROOT, "%0" + width + "d", value);
  }

  private static long parseDigits(String value, int width) {
    require(value, "[0-9]{" + width + "}", "numeric field");
    return Long.parseLong(value);
  }
}
