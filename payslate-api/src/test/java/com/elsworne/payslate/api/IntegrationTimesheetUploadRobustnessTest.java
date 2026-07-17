package com.elsworne.payslate.api;

import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.HEADER;
import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.LINE_TWO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("docker")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mvc.problemdetails.enabled=true")
class IntegrationTimesheetUploadRobustnessTest {
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

  @LocalServerPort int port;
  @MockitoBean JwtDecoder decoder;

  @Test
  void nulInEmployeeNameReturnsBadRequestThroughPostgres() throws Exception {
    UploadHttpSupport.authenticate(decoder, "postgres-upload-regression");
    var response =
        UploadHttpSupport.upload(port, HEADER + "Liam\u0000 Walsh,2026-07-06,09:00,17:00\n");
    assertThat(UploadHttpSupport.problem(response, 400).path("detail").asString())
        .contains(LINE_TWO);
  }
}
