package com.elsworne.payslate.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
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
        .andExpect(status().isBadRequest());
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
        .andExpect(status().isBadRequest());
    mvc.perform(post("/v1/pay-runs/" + runId + "/calculate").with(officer("tenant-a")))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/pay-runs/" + runId + "/payslips/" + employeeId).with(officer("tenant-b")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/v1/pay-runs/" + runId + "/payslips/" + employeeId)
                .with(employee("tenant-a", "999")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/v1/pay-runs/" + runId + "/payslips/" + employeeId)
                .with(employee("tenant-a", Long.toString(employeeId))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/v1/pay-runs")
                .with(employee("tenant-a", Long.toString(employeeId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"2026-02-23\",\"periodEnd\":\"2026-03-01\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v1/pay-runs/" + runId)).andExpect(status().isUnauthorized());
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
