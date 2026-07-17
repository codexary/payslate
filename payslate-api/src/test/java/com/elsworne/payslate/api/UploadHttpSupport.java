package com.elsworne.payslate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class UploadHttpSupport {
  private UploadHttpSupport() {}

  static void authenticate(JwtDecoder decoder, String employer) {
    when(decoder.decode("upload-test"))
        .thenReturn(
            Jwt.withTokenValue("upload-test")
                .header("alg", "none")
                .subject("officer")
                .claim("employer_id", employer)
                .claim("roles", List.of("PAYROLL_OFFICER"))
                .build());
  }

  static HttpResponse<String> upload(int port, String csv) throws Exception {
    String boundary = "timesheet-regression-boundary";
    String body =
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"timesheet.csv\"\r\n"
            + "Content-Type: text/csv\r\n\r\n"
            + csv
            + "\r\n--"
            + boundary
            + "--\r\n";
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/timesheets"))
              .header("Authorization", "Bearer upload-test")
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  static JsonNode problem(HttpResponse<String> response, int status) {
    assertEquals(status, response.statusCode(), "HTTP status");
    assertEquals(
        "application/problem+json",
        response.headers().firstValue("Content-Type").orElse(""),
        "problem content type");
    var body = new JsonMapper().readTree(response.body());
    assertEquals(status, body.path("status").asInt(), "problem status");
    return body;
  }
}
