package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("docker")
@SpringBootTest
class IntegrationEmployeeSearchSecurityTest {
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

  @Autowired PayrollService service;

  // A synthetic name whose quote and tautology would reopen the SQL string if the
  // search text were concatenated instead of bound. It is employee data, not a person.
  private static final String QUOTED_NAME = "O' OR '1'='1' --";

  @Test
  void quotedNameIsSearchDataAndMatchesOnlyThatEmployee() {
    long matching =
        service.createEmployee(
            "tenant-a",
            QUOTED_NAME,
            "PERMANENT",
            new BigDecimal("31.00"),
            "123-456",
            "00000001",
            BigDecimal.ZERO);
    service.createEmployee(
        "tenant-a",
        "Other Synthetic",
        "PERMANENT",
        new BigDecimal("99.00"),
        "123-456",
        "00000002",
        BigDecimal.ZERO);

    assertThat(service.searchEmployees(QUOTED_NAME))
        .extracting(row -> ((Number) row.get("id")).longValue())
        .containsExactly(matching);
  }
}
