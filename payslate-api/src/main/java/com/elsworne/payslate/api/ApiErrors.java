package com.elsworne.payslate.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(RecordNotFoundException.class)
  ProblemDetail notFound() {
    var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Record not found");
    detail.setTitle("Not found");
    return detail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException error) {
    var detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            error instanceof InvalidRequestException ? error.getMessage() : "Invalid request");
    detail.setTitle("Invalid request");
    return detail;
  }

  @ExceptionHandler(PayRunConflictException.class)
  ProblemDetail conflict(PayRunConflictException error) {
    var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    detail.setTitle("Invalid pay-run transition");
    return detail;
  }

  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  void securityFailure(RuntimeException error) {
    // Spring Security writes every 401 and 403, so these leave through its handlers.
    throw error;
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception error, HttpServletRequest request) {
    if (error instanceof ErrorResponse response)
      return ResponseEntity.status(response.getStatusCode())
          .headers(response.getHeaders())
          .body(ProblemDetail.forStatus(response.getStatusCode()));
    logFailure(error, request);
    return ResponseEntity.internalServerError()
        .body(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception error,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) logFailure(error, ((ServletWebRequest) request).getRequest());
    return super.handleExceptionInternal(
        error, ProblemDetail.forStatus(status), headers, status, request);
  }

  static void logFailure(Throwable error, HttpServletRequest request) {
    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    LOG.error(
        "Request failed: method={} route={}",
        request.getMethod().replace('\r', '_').replace('\n', '_'),
        route == null ? "UNKNOWN" : route.toString().replace('\r', '_').replace('\n', '_'),
        error);
  }
}
