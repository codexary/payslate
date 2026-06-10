package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("docker")
@SpringBootTest
class IntegrationPostgresMigrationTest {
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

  @Test
  void flywaySchemaStoresAnEmployeeAndPayRun() {
    assertThat(
            service.createEmployee(
                "Docker Test", "PERMANENT", new java.math.BigDecimal("20"), "123-456", "12345678"))
        .isPositive();
    assertThat(service.state(service.createRun())).isEqualTo("DRAFT");
  }
}
