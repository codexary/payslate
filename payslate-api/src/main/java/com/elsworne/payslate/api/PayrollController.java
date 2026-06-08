package com.elsworne.payslate.api;

import com.elsworne.payslate.engine.payrun.PayRun.Event;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
      String name, String employment, BigDecimal hourlyRate, String bsb, String account) {}

  private final PayrollService service;

  PayrollController(PayrollService service) {
    this.service = service;
  }

  @PostMapping("/employees")
  public Map<String, Long> employee(@RequestBody EmployeeInput input) {
    return Map.of(
        "id",
        service.createEmployee(
            input.name(), input.employment(), input.hourlyRate(), input.bsb(), input.account()));
  }

  @PostMapping(path = "/timesheets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Integer> timesheets(@RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return Map.of(
        "rows",
        service.upload(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)));
  }

  @PostMapping("/pay-runs")
  public Map<String, Long> run() {
    return Map.of("id", service.createRun());
  }

  @GetMapping("/pay-runs/{id}")
  public Map<String, String> state(@PathVariable long id) {
    return Map.of("state", service.state(id));
  }

  @PostMapping("/pay-runs/{id}/{event}")
  public Map<String, String> transition(@PathVariable long id, @PathVariable String event) {
    return Map.of(
        "state", service.transition(id, Event.valueOf(event.toUpperCase(java.util.Locale.ROOT))));
  }

  @GetMapping("/pay-runs/{id}/payslips/{employeeId}")
  public Map<String, Object> payslip(@PathVariable long id, @PathVariable long employeeId) {
    return service.payslip(id, employeeId);
  }

  @GetMapping(path = "/pay-runs/{id}/aba", produces = "text/plain")
  public ResponseEntity<String> aba(@PathVariable long id) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=pay-run-" + id + ".aba")
        .body(service.aba(id));
  }
}
