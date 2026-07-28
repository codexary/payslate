package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationUploadConcurrencyTest {
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
  void concurrentDuplicateUploadsAdmitExactlyOne() throws Exception {
    UploadHttpSupport.authenticate(decoder, "postgres-upload-concurrency");
    try (var client = HttpClient.newHttpClient()) {
      var employee =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/employees"))
                  .header("Authorization", "Bearer upload-test")
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          """
                          {"name":"Liam Walsh","employment":"PERMANENT","hourlyRate":20,
                           "bsb":"123-456","account":"12345678"}
                          """))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(employee.statusCode()).as("employee creation status").isEqualTo(200);
    }

    String csv = "employee,date,start,end\nLiam Walsh,2026-02-23,09:00,17:00\n";
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    Callable<Integer> upload =
        () -> {
          ready.countDown();
          assertThat(start.await(10, TimeUnit.SECONDS)).as("upload start signal").isTrue();
          return UploadHttpSupport.upload(port, csv).statusCode();
        };

    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(upload);
      var second = executor.submit(upload);
      assertThat(ready.await(10, TimeUnit.SECONDS)).as("both upload workers ready").isTrue();
      start.countDown();
      var statuses =
          java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(statuses)
          .as("exactly one duplicate upload succeeds and the other is rejected")
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }
}
