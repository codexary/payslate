package com.elsworne.payslate.api;

final class RecordNotFoundException extends RuntimeException {
  RecordNotFoundException() {
    super("Record not found");
  }
}
