package com.elsworne.payslate.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.mvc.problemdetails.enabled=true")
@AutoConfigureMockMvc
class SystemEmployeeFieldsTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void rejectsNameLongerThanOneHundredCharacters() throws Exception {
    badRequest("x".repeat(101), "20", "0", "name");
  }

  @Test
  void rejectsNulInName() throws Exception {
    badRequest("Liam\u0000 Walsh", "20", "0", "name");
  }

  @Test
  void rejectsControlCharacterInName() throws Exception {
    badRequest("Liam\u001f Walsh", "20", "0", "name");
  }

  @Test
  void rejectsHourlyRateBeyondColumnRange() throws Exception {
    badRequest("Liam Walsh", "1e20", "0", "hourlyRate");
  }

  @Test
  void rejectsHourlyRateWithThreeDecimals() throws Exception {
    badRequest("Liam Walsh", "12.345", "0", "hourlyRate");
  }

  @Test
  void rejectsDeductionsBeyondColumnRange() throws Exception {
    badRequest("Liam Walsh", "20", "100000000000000", "deductions");
  }

  @Test
  void acceptsOneHundredCharacterName() throws Exception {
    create("x".repeat(100), "20", "0")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber());
  }

  @Test
  void acceptsMaximumHourlyRate() throws Exception {
    create("Liam Walsh", "9999.99", "0")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber());
  }

  @Test
  void acceptsZeroHourlyRate() throws Exception {
    create("Liam Walsh", "0", "0")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber());
  }

  private void badRequest(String name, String rate, String deductions, String field)
      throws Exception {
    create(name, rate, deductions)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.detail").value(containsString(field)));
  }

  private ResultActions create(String name, String rate, String deductions) throws Exception {
    return mvc.perform(
        post("/v1/employees")
            .with(
                jwt()
                    .jwt(token -> token.claim("employer_id", "employee-fields"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(name, rate, deductions)));
  }

  static String body(String name, String rate, String deductions) {
    return new JsonMapper()
        .writeValueAsString(
            Map.of(
                "name",
                name,
                "employment",
                "PERMANENT",
                "hourlyRate",
                new BigDecimal(rate),
                "bsb",
                "123-456",
                "account",
                "12345678",
                "deductions",
                new BigDecimal(deductions)));
  }
}
