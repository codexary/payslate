package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "spring.mvc.problemdetails.enabled=true")
@AutoConfigureMockMvc
class SystemApiErrorsRobustnessTest {
  static final String GENERIC_SERVER_TITLE = "Internal Server Error";
  private static final String FAILURE_TEXT = "database connection failed: private diagnostic";

  @Autowired MockMvc mvc;
  @MockitoBean PayrollService service;
  @MockitoBean JwtDecoder decoder;

  @Test
  void unknownEventDoesNotExposeJavaNames() throws Exception {
    var response =
        mvc.perform(post("/v1/pay-runs/1/p").with(officer()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString())
        .doesNotContain("com.elsworne", "java.", "PayRun", "Event", "IllegalArgumentException");
  }

  @Test
  void unexpectedFailureReturnsOnlyGenericProblemDetails() throws Exception {
    when(service.state("error-regression", 1L))
        .thenThrow(new DataAccessResourceFailureException(FAILURE_TEXT));
    var result =
        assertDoesNotThrow(
            () -> mvc.perform(get("/v1/pay-runs/1").with(officer())),
            "Unexpected failures must be handled as HTTP 500 problem details");
    var response =
        result
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value(GENERIC_SERVER_TITLE))
            .andExpect(jsonPath("$.detail").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.timestamp").doesNotHaveJsonPath())
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString())
        .doesNotContain(
            FAILURE_TEXT, "DataAccessResourceFailureException", "org.springframework", "java.");
  }

  @Test
  void typeMismatchStillReturnsBadRequest() throws Exception {
    mvc.perform(get("/v1/pay-runs/abc").with(officer())).andExpect(status().isBadRequest());
  }

  @Test
  void unreadableJsonStillReturnsBadRequest() throws Exception {
    mvc.perform(
            post("/v1/pay-runs")
                .with(officer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void firewallRejectionReturnsBadRequestProblemDetails() throws Exception {
    var result =
        mvc.perform(post(URI.create("/v1/pay-runs/1/%0D")).with(officer()))
            .andExpect(status().isBadRequest())
            .andReturn();
    // No controller was selected: this request exercises the active security firewall.
    assertThat(result.getHandler()).isNull();
    verifyNoInteractions(service);
    content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
    jsonPath("$.status").value(400).match(result);
    jsonPath("$.timestamp").doesNotHaveJsonPath().match(result);
  }

  private static RequestPostProcessor officer() {
    return jwt()
        .jwt(token -> token.claim("employer_id", "error-regression"))
        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"));
  }
}
