package com.elsworne.payslate.api;

import com.elsworne.payslate.engine.PayCalendar;
import com.elsworne.payslate.engine.payrun.PayRun.Event;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
public class PayrollController {
  public record EmployeeInput(
      String name,
      String employment,
      BigDecimal hourlyRate,
      String bsb,
      String account,
      BigDecimal deductions) {}

  public record RunInput(LocalDate periodStart, LocalDate periodEnd) {}

  public record CalendarRunInput(
      PayCalendar.Frequency frequency, LocalDate anchorPayDate, LocalDate payDate) {}

  private final PayrollService service;

  PayrollController(PayrollService service) {
    this.service = service;
  }

  @PostMapping("/employees")
  public Map<String, Long> employee(
      @AuthenticationPrincipal Jwt jwt, @RequestBody EmployeeInput input) {
    return Map.of(
        "id",
        service.createEmployee(
            employer(jwt),
            input.name(),
            input.employment(),
            input.hourlyRate(),
            input.bsb(),
            input.account(),
            input.deductions() == null ? BigDecimal.ZERO : input.deductions()));
  }

  @GetMapping("/employees/{id}")
  public Map<String, Object> employee(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return service.employee(employer(jwt), id);
  }

  @GetMapping("/employees/search")
  public java.util.List<Map<String, Object>> searchEmployees(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("q") String query) {
    return service.searchEmployees(employer(jwt), query);
  }

  @PostMapping(path = "/timesheets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Integer> timesheets(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return Map.of(
        "rows",
        service.upload(
            employer(jwt), new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)));
  }

  @PostMapping("/pay-runs")
  public Map<String, Long> run(@AuthenticationPrincipal Jwt jwt, @RequestBody RunInput input) {
    return Map.of("id", service.createRun(employer(jwt), input.periodStart(), input.periodEnd()));
  }

  @PostMapping("/pay-runs/calendar")
  public Map<String, Long> calendarRun(
      @AuthenticationPrincipal Jwt jwt, @RequestBody CalendarRunInput input) {
    return Map.of(
        "id",
        service.createCalendarRun(
            employer(jwt), input.frequency(), input.anchorPayDate(), input.payDate()));
  }

  @GetMapping("/pay-runs/{id}")
  public Map<String, String> state(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return Map.of("state", service.state(employer(jwt), id));
  }

  @PostMapping("/pay-runs/{id}/{event}")
  public Map<String, String> transition(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable String event) {
    return Map.of(
        "state",
        service.transition(
            employer(jwt), id, Event.valueOf(event.toUpperCase(java.util.Locale.ROOT))));
  }

  @GetMapping("/pay-runs/{id}/payslips/{employeeId}")
  public Map<String, Object> payslip(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long employeeId) {
    checkPayslipAccess(jwt, employeeId);
    return service.payslip(employer(jwt), id, employeeId);
  }

  @GetMapping(path = "/pay-runs/{id}/aba", produces = "text/plain")
  public ResponseEntity<String> aba(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=pay-run-" + id + ".aba")
        .body(service.aba(employer(jwt), id));
  }

  private static String employer(Jwt jwt) {
    String employer = jwt.getClaimAsString("employer_id");
    if (employer == null || employer.isBlank()) throw new AccessDeniedException("Missing employer");
    return employer;
  }

  private static void checkPayslipAccess(Jwt jwt, long employeeId) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) throw new AccessDeniedException("Authentication required");
    boolean officer =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_PAYROLL_OFFICER".equals(authority.getAuthority()));
    if (!officer && !Long.toString(employeeId).equals(jwt.getClaimAsString("employee_id")))
      throw new AccessDeniedException("Employee may only read own payslip");
  }
}
