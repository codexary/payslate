package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HttpMetricsTest {
  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;

  @Test
  void recordsRouteAndStatusWithoutRequestValues() throws Exception {
    String id = "987654321";
    mvc.perform(
            get("/v1/employees/" + id)
                .param("q", "private-search")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("private-user")
                                    .claim("employer_id", "private-employer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"))))
        .andExpect(status().isNotFound());

    var timer =
        registry
            .get("http.server.requests")
            .tags("uri", "/v1/employees/{id}", "method", "GET", "status", "404")
            .timer();
    assertThat(timer.count()).isPositive();
    assertThat(timer.getId().getTags())
        .extracting(Tag::getKey)
        .containsExactlyInAnyOrder("uri", "method", "status");
    assertThat(registry.getMeters())
        .allSatisfy(
            meter ->
                assertThat(meter.getId().getTags())
                    .allSatisfy(
                        tag ->
                            assertThat(tag.getValue())
                                .doesNotContain(
                                    id, "private-user", "private-employer", "private-search")));
  }
}
