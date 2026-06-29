package com.elsworne.payslate.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(RecordNotFoundException.class)
  ProblemDetail notFound(RecordNotFoundException error) {
    var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    detail.setTitle("Not found");
    return detail;
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  ProblemDetail badRequest(RuntimeException error) {
    var detail =
        ProblemDetail.forStatus(
            error instanceof IllegalStateException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
    detail.setTitle(
        error instanceof IllegalStateException ? "Invalid pay-run transition" : "Invalid request");
    detail.setDetail(error.getMessage());
    return detail;
  }
}
