package com.elsworne.payslate.api;

final class ShiftOverlapConflictException extends RuntimeException {
  ShiftOverlapConflictException(String message) {
    super(message);
  }
}
