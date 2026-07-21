package com.elsworne.payslate.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiErrorController implements ErrorController {
  @RequestMapping("/error")
  ProblemDetail error(HttpServletRequest request) {
    Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = code instanceof Integer value ? value : 500;
    Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    if (status >= 500 && error instanceof Throwable failure) ApiErrors.logFailure(failure, request);
    return ProblemDetail.forStatus(HttpStatus.valueOf(status));
  }
}
