package com.elsworne.payslate.api;

final class EmployeeName {
  private static final int MAX_LENGTH = 100;

  private EmployeeName() {}

  static String normalize(String value) {
    if (value == null) throw new InvalidRequestException("name is required");
    if (value.chars().anyMatch(c -> c < 32 || c == 127))
      throw new InvalidRequestException("name contains a control character");
    String name = value.trim();
    if (name.isBlank() || name.length() > MAX_LENGTH)
      throw new InvalidRequestException("name must contain 1–100 characters");
    return name;
  }
}
