package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elsworne.payslate.engine.payrun.PayRun.Event;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntegrationTenantIsolationTest {
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired PayrollService service;

  @Test
  void oneEmployerCannotReadAnotherEmployersEmployeesOrPayRuns() throws Exception {
    var created =
        mvc.perform(
                post("/v1/employees")
                    .with(officer("tenant-a"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Alice Test\",\"employment\":\"PERMANENT\",\"hourlyRate\":20,\"bsb\":\"123-456\",\"account\":\"12345678\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long employeeId = id(created.getResponse().getContentAsString());
    mvc.perform(get("/v1/employees/" + employeeId).with(officer("tenant-b")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/v1/employees/" + employeeId).with(officer("tenant-a")))
        .andExpect(status().isOk());

    var csv =
        new MockMultipartFile(
            "file",
            "timesheet.csv",
            "text/csv",
            "employee,date,start,end\nAlice Test,2026-02-23,09:00,17:00\n"
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer("tenant-b")))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer("tenant-a")))
        .andExpect(status().isOk());

    var createdRun =
        mvc.perform(
                post("/v1/pay-runs")
                    .with(officer("tenant-a"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"periodStart\":\"2026-02-23\",\"periodEnd\":\"2026-03-01\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long runId = id(createdRun.getResponse().getContentAsString());
    mvc.perform(get("/v1/pay-runs/" + runId).with(officer("tenant-b")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/v1/pay-runs/" + runId + "/approve").with(officer("tenant-b")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/v1/pay-runs/" + runId + "/calculate").with(officer("tenant-a")))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/pay-runs/" + runId + "/payslips/" + employeeId).with(officer("tenant-b")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/v1/pay-runs/" + runId + "/payslips/" + employeeId)
                .with(employee("tenant-a", "999")))
        .andExpect(status().isForbidden())
        .andExpect(securityProblem(403, "Forbidden", "Access is denied."));
    mvc.perform(
            get("/v1/pay-runs/" + runId + "/payslips/" + employeeId)
                .with(employee("tenant-a", Long.toString(employeeId))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/v1/pay-runs")
                .with(employee("tenant-a", Long.toString(employeeId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"2026-02-23\",\"periodEnd\":\"2026-03-01\"}"))
        .andExpect(status().isForbidden())
        .andExpect(securityProblem(403, "Forbidden", "Access is denied."));
    mvc.perform(get("/v1/pay-runs/" + runId))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(securityProblem(401, "Unauthorized", "Authentication is required."));
  }

  @Test
  void anotherEmployersRecordAndAMissingRecordGiveIdenticalResponses() throws Exception {
    var records = setUpRecords();
    String employeePath = "/v1/employees/" + records.employeeId();
    String runPath = "/v1/pay-runs/" + records.runId();
    String slipPath = runPath + "/payslips/" + records.employeeId();
    String missingRunPath = "/v1/pay-runs/" + Long.MAX_VALUE;

    assertSameNotFound(employeePath, "/v1/employees/" + Long.MAX_VALUE);
    assertSameNotFound(runPath, missingRunPath);
    assertSameNotFound(slipPath, missingRunPath + "/payslips/" + records.employeeId());
    assertSameNotFound(runPath + "/aba", missingRunPath + "/aba");

    // A visible run also hides whether its employee or payslip is missing.
    assertSameNotFound(
        slipPath, "/v1/pay-runs/" + records.ownRunId() + "/payslips/" + Long.MAX_VALUE);
    assertSameNotFound(
        slipPath, "/v1/pay-runs/" + records.ownRunId() + "/payslips/" + records.ownEmployeeId());
  }

  private Records setUpRecords() {
    long employeeId =
        service.createEmployee(
            "tenant-a",
            "Alice Test",
            "PERMANENT",
            new BigDecimal("20"),
            "123-456",
            "12345678",
            BigDecimal.ZERO);
    service.upload("tenant-a", "employee,date,start,end\nAlice Test,2026-02-23,09:00,17:00\n");
    long runId = service.createRun("tenant-a", LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 1));
    for (var event : new Event[] {Event.CALCULATE, Event.APPROVE, Event.PAY})
      service.transition("tenant-a", runId, event);
    long ownEmployeeId =
        service.createEmployee(
            "tenant-b",
            "Bob Test",
            "PERMANENT",
            new BigDecimal("20"),
            "123-456",
            "12345678",
            BigDecimal.ZERO);
    long ownRunId =
        service.createRun("tenant-b", LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 1));
    return new Records(employeeId, runId, ownEmployeeId, ownRunId);
  }

  private record Records(long employeeId, long runId, long ownEmployeeId, long ownRunId) {}

  private void assertSameNotFound(String foreignPath, String missingPath) throws Exception {
    mvc.perform(get(foreignPath).with(officer("tenant-a"))).andExpect(status().isOk());
    mvc.perform(get(foreignPath))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(securityProblem(401, "Unauthorized", "Authentication is required."));
    mvc.perform(get(foreignPath).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
        .andExpect(status().isForbidden())
        .andExpect(securityProblem(403, "Forbidden", "Access is denied."));
    var foreign = mvc.perform(get(foreignPath).with(officer("tenant-b"))).andReturn().getResponse();
    var missing = mvc.perform(get(missingPath).with(officer("tenant-b"))).andReturn().getResponse();
    assertAll(
        () -> assertThat(foreign.getStatus()).as("cross-employer status").isEqualTo(404),
        () -> assertThat(missing.getStatus()).as("missing-record status").isEqualTo(404),
        () -> assertThat(foreign.getStatus()).isEqualTo(missing.getStatus()),
        () -> assertThat(foreign.getContentType()).isEqualTo("application/problem+json"),
        () -> assertThat(foreign.getContentType()).isEqualTo(missing.getContentType()),
        () ->
            assertThat(normalizedBody(foreign, foreignPath))
                .containsExactly(normalizedBody(missing, missingPath)));
  }

  private static ResultMatcher securityProblem(int status, String title, String detail) {
    return result -> {
      content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
      content()
          .json(
              """
              {"title":"%s","status":%d,"detail":"%s","instance":"%s"}
              """
                  .formatted(title, status, detail, result.getRequest().getRequestURI()),
              JsonCompareMode.STRICT)
          .match(result);
    };
  }

  private static byte[] normalizedBody(MockHttpServletResponse response, String path)
      throws Exception {
    // Only instance varies per request: Spring sets it to the request URI.
    // Replace its value in the raw body; preserve every other byte, including whitespace and order.
    String body = response.getContentAsString(StandardCharsets.UTF_8);
    String instance = "\"instance\":\"" + path + "\"";
    assertThat(body).containsOnlyOnce(instance);
    return body.replace(instance, "\"instance\":\"\"").getBytes(StandardCharsets.UTF_8);
  }

  private static RequestPostProcessor officer(String employer) {
    return jwt()
        .jwt(token -> token.claim("employer_id", employer))
        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"));
  }

  private static RequestPostProcessor employee(String employer, String employeeId) {
    return jwt()
        .jwt(token -> token.claim("employer_id", employer).claim("employee_id", employeeId))
        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
  }

  private static long id(String json) {
    return Long.parseLong(json.replaceAll("[^0-9]", ""));
  }
}
