package com.elsworne.payslate.bank;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.time.LocalDate;
import java.util.List;

class AbaReferenceFuzzTest {
  @FuzzTest(maxDuration = "2m")
  void reference(byte[] data) {
    StringBuilder reference = new StringBuilder();
    for (int i = 0; i < Math.min(data.length, 18); i++) {
      int b = data[i] & 0xff;
      reference.append(b >= 0x20 && b <= 0x7e ? (char) b : 'A');
    }
    if (reference.isEmpty()) reference.append('A');

    String text;
    try {
      text =
          new AbaFile()
              .write(
                  new AbaFile.Batch(
                      "TST",
                      "ELSWORNE",
                      "123456",
                      "PAYROLL",
                      LocalDate.of(2026, 2, 20),
                      "123-456",
                      "87654321",
                      List.of(
                          new AbaFile.Payment(
                              "123-456", "12345678", "Synthetic One", reference.toString(), 100))));
    } catch (IllegalArgumentException ignored) {
      return;
    }

    try {
      new AbaFile().parse(text);
    } catch (IllegalArgumentException ignored) {
      // The parser must only reject with IllegalArgumentException; other exceptions are findings.
    }
  }
}
