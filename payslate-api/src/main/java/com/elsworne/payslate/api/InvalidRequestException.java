package com.elsworne.payslate.api;

final class InvalidRequestException extends IllegalArgumentException {
  InvalidRequestException(String message) {
    super(message);
  }

  InvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
