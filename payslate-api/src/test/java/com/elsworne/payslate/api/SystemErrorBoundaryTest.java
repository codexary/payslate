package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.RequestDispatcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SystemErrorBoundaryTest {
  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;
  @MockitoBean PayrollService service;
  @MockitoBean JwtDecoder decoder;

  @Test
  void firewallUsesOnlyAFixedUriTag() throws Exception {
    String rawPath = "/v1/pay-runs/123/crafted%0Dvalue";
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      var problem = UploadHttpSupport.problem(response, 400);
      assertThat(problem.path("detail").asString()).isEqualTo("The request was rejected.");
      assertThat(response.body()).doesNotContain("crafted", "%0D", "timestamp");
    }
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              var timer =
                  registry
                      .get("http.server.requests")
                      .tags("uri", "UNKNOWN", "method", "POST", "status", "400")
                      .timer();
              assertThat(timer.count()).isEqualTo(1);
            });
    assertThat(registry.getMeters())
        .allSatisfy(
            meter ->
                assertThat(meter.getId().getTags())
                    .allSatisfy(
                        tag ->
                            assertThat(tag.getValue())
                                .doesNotContain(rawPath, "crafted", "%0D", "123")));
  }

  @Test
  void libraryArgumentMessagesStayPrivate() throws Exception {
    when(service.state("boundary-test", 1))
        .thenThrow(new IllegalArgumentException("private.library.Type: raw value"));
    mvc.perform(get("/v1/pay-runs/1").with(officer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid request"));
  }

  @Test
  void unexpectedFailureLogsOnceWithTheRouteTemplate() throws Exception {
    when(service.state("boundary-test", 2))
        .thenThrow(new DataAccessResourceFailureException("private diagnostic"));
    Logger logger = (Logger) LoggerFactory.getLogger(ApiErrors.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      mvc.perform(get("/v1/pay-runs/2").queryParam("secret", "private-query").with(officer()))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").doesNotHaveJsonPath());
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getLevel().toString()).isEqualTo("ERROR");
      assertThat(appender.list.getFirst().getFormattedMessage())
          .isEqualTo("Request failed: method=GET route=/v1/pay-runs/{id}");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void servletErrorDispatchUsesProblemDetails() throws Exception {
    mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.timestamp").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.path").doesNotHaveJsonPath());
  }

  @Test
  void missingPartAndUnsupportedMethodKeepTheirStatus() throws Exception {
    mvc.perform(post("/v1/timesheets").contentType(MediaType.MULTIPART_FORM_DATA).with(officer()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get("/v1/timesheets").with(officer()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void controllerAccessDenialMatchesTheFilterDenial() throws Exception {
    var controller =
        mvc.perform(get("/v1/pay-runs/1/payslips/2").with(employee("3")))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
            .andReturn()
            .getResponse();
    var filter =
        mvc.perform(post("/v1/pay-runs").with(employee("3")))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
            .andReturn()
            .getResponse();
    assertThat(withoutInstance(controller)).isEqualTo(withoutInstance(filter));
  }

  @Test
  void authenticationFailureInsideARouteIsNotAServerError() throws Exception {
    when(service.state("boundary-test", 1L))
        .thenThrow(new InsufficientAuthenticationException("token no longer accepted"));
    mvc.perform(get("/v1/pay-runs/1").with(officer()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Authentication is required."));
  }

  private static Map<?, ?> withoutInstance(MockHttpServletResponse response) throws Exception {
    var body = new JsonMapper().readValue(response.getContentAsString(), Map.class);
    body.remove("instance");
    return body;
  }

  private static RequestPostProcessor employee(String employeeId) {
    return jwt()
        .jwt(token -> token.claim("employer_id", "boundary-test").claim("employee_id", employeeId))
        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
  }

  private static RequestPostProcessor officer() {
    return jwt()
        .jwt(token -> token.claim("employer_id", "boundary-test"))
        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"));
  }
}
