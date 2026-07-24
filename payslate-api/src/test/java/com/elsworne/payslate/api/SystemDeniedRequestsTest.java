package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SystemDeniedRequestsTest {
  private static final String TOKEN = "private-bearer-token";
  private static final String ID = "987654321";
  private static final String SUBJECT = "synthetic-officer";
  private static final String EMPLOYER = "synthetic-employer";
  private final Logger logger = (Logger) LoggerFactory.getLogger(DeniedRequests.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;
  @MockitoBean JwtDecoder decoder;

  @BeforeEach
  void captureEvents() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopCapture() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void unauthorizedRequestLogsOneEventWithoutUntrustedClaims() throws Exception {
    when(decoder.decode(TOKEN)).thenThrow(new BadJwtException("private-token-diagnostic"));
    mvc.perform(request("/v1/employees/" + ID)).andExpect(status().isUnauthorized());
    assertEvent(401, "/v1/employees/{id}", false, false);
  }

  @Test
  void forbiddenRequestLogsOneEventWithVerifiedClaims() throws Exception {
    authenticate("EMPLOYEE", true);
    mvc.perform(request("/v1/employees/" + ID)).andExpect(status().isForbidden());
    assertEvent(403, "/v1/employees/{id}", true, true);
  }

  @Test
  void missingRecordLogsOneEventWithVerifiedClaims() throws Exception {
    authenticate("PAYROLL_OFFICER", true);
    mvc.perform(request("/v1/employees/" + ID)).andExpect(status().isNotFound());
    assertEvent(404, "/v1/employees/{id}", true, true);
  }

  @Test
  void controllerDenialAlsoLogsExactlyOnce() throws Exception {
    authenticate("EMPLOYEE", true);
    mvc.perform(request("/v1/pay-runs/" + ID + "/payslips/" + ID))
        .andExpect(status().isForbidden());
    assertEvent(403, "/v1/pay-runs/{id}/payslips/{employeeId}", true, true);
  }

  @Test
  void missingEmployerClaimIsOmitted() throws Exception {
    authenticate("PAYROLL_OFFICER", false);
    mvc.perform(request("/v1/employees/" + ID)).andExpect(status().isForbidden());
    assertEvent(403, "/v1/employees/{id}", true, false);
  }

  @Test
  void unknownRoute404IsNotASecurityEvent() throws Exception {
    authenticate("PAYROLL_OFFICER", true);
    double before = totalDenials();
    mvc.perform(request("/v1/private-unknown-route/" + ID)).andExpect(status().isNotFound());
    assertThat(appender.list).isEmpty();
    assertThat(totalDenials()).isEqualTo(before);
  }

  @Test
  void unknownRouteDenialUsesAFixedFallback() throws Exception {
    mvc.perform(get("/v1/private-unknown-route/" + ID).queryParam("q", "private-query"))
        .andExpect(status().isUnauthorized());
    assertEvent(401, "UNKNOWN", false, false);
  }

  @Test
  void preRoutingDenialChoosesTheMostSpecificTemplate() throws Exception {
    mvc.perform(get("/v1/employees/search").queryParam("q", "private-query"))
        .andExpect(status().isUnauthorized());
    assertEvent(401, "/v1/employees/search", false, false);
  }

  @Test
  void preRoutingTemplateAlsoMatchesTheRequestMethod() throws Exception {
    mvc.perform(get("/v1/pay-runs/calendar")).andExpect(status().isUnauthorized());
    assertEvent(401, "/v1/pay-runs/{id}", false, false);
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  void countersStartAtZeroAndExposeTheFirstDenial() throws Exception {
    var counter =
        registry
            .find("payslate.access.denied")
            .tags("route", "/v1/pay-runs/{id}/aba", "status", "401")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isZero();
    assertThat(registry.get("payslate.access.denied").counters())
        .allSatisfy(registered -> assertThat(registered.count()).isZero())
        .extracting(registered -> registered.getId().getTag("route"))
        .allSatisfy(route -> assertThat(route).matches("/v1/.*|UNKNOWN"));
    for (String status : List.of("401", "403"))
      assertThat(
              registry
                  .find("payslate.access.denied")
                  .tags("route", "UNKNOWN", "status", status)
                  .counter())
          .isNotNull();
    assertThat(
            registry
                .find("payslate.access.denied")
                .tags("route", "UNKNOWN", "status", "404")
                .counter())
        .isNull();
    mvc.perform(get("/v1/pay-runs/" + ID + "/aba")).andExpect(status().isUnauthorized());
    assertThat(counter.count()).isEqualTo(1);
  }

  @Test
  void countersIncrementOnceAndHaveOnlyRouteAndStatusTags() throws Exception {
    for (int code : List.of(401, 403, 404)) {
      if (code == 401) when(decoder.decode(TOKEN)).thenThrow(new BadJwtException("invalid token"));
      else authenticate(code == 403 ? "EMPLOYEE" : "PAYROLL_OFFICER", true);
      var counter =
          registry.counter(
              "payslate.access.denied",
              "route",
              "/v1/employees/{id}",
              "status",
              Integer.toString(code));
      double before = counter.count();
      mvc.perform(request("/v1/employees/" + ID)).andExpect(status().is(code));
      assertThat(counter.count()).isEqualTo(before + 1);
    }
    assertThat(registry.get("payslate.access.denied").counters())
        .allSatisfy(
            counter -> {
              assertThat(counter.getId().getTags())
                  .extracting(Tag::getKey)
                  .containsExactlyInAnyOrder("route", "status");
              assertThat(counter.getId().getTags())
                  .extracting(Tag::getValue)
                  .allSatisfy(
                      value ->
                          assertThat(value)
                              .doesNotContain(
                                  TOKEN, ID, SUBJECT, EMPLOYER, "private-query", "private-body"));
            });
  }

  private double totalDenials() {
    return registry.find("payslate.access.denied").counters().stream()
        .mapToDouble(counter -> counter.count())
        .sum();
  }

  private void authenticate(String role, boolean employer) {
    var token =
        Jwt.withTokenValue(TOKEN)
            .header("alg", "RS256")
            .subject(SUBJECT)
            .claim("roles", List.of(role))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    if (employer) token.claim("employer_id", EMPLOYER);
    org.mockito.Mockito.doReturn(token.build()).when(decoder).decode(TOKEN);
  }

  private static MockHttpServletRequestBuilder request(String path) {
    return get(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .queryParam("q", "private-query")
        .content("private-body");
  }

  private void assertEvent(int status, String route, boolean subject, boolean employer) {
    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getFormattedMessage()).isEqualTo("Request denied");
    assertThat(event.getThrowableProxy()).isNull();
    Map<String, Object> fields = new LinkedHashMap<>();
    event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
    Map<String, Object> expected =
        new LinkedHashMap<>(
            Map.of(
                "event.action",
                "access-denied",
                "event.outcome",
                "failure",
                "http.response.status_code",
                status,
                "http.request.method",
                "GET",
                "http.route",
                route));
    if (subject) expected.put("sub", SUBJECT);
    if (employer) expected.put("employer_id", EMPLOYER);
    assertThat(fields).containsExactlyInAnyOrderEntriesOf(expected);
    var encoder = new StructuredLogEncoder();
    encoder.setContext(logger.getLoggerContext());
    encoder.setFormat("ecs");
    encoder.start();
    String json;
    try {
      json = new String(encoder.encode(event), StandardCharsets.UTF_8);
    } finally {
      encoder.stop();
    }
    var serialized = new JsonMapper().readTree(json);
    expected.forEach(
        (key, value) ->
            assertThat(serialized.at("/" + key.replace(".", "/")).asText())
                .as(json)
                .isEqualTo(value.toString()));
    assertThat(json)
        .doesNotContain(
            TOKEN,
            "Bearer",
            "private-query",
            "private-body",
            ID,
            "private-unknown-route",
            "private-token-diagnostic");
  }
}
