package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elsworne.payslate.bank.AbaFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemAbaAccountTitlesTest {
  @Autowired MockMvc mvc;

  @Test
  void exportsNamesOutsideTheBankCharacterSetAndWidth() throws Exception {
    var names = List.of("Renée Dubois", "A".repeat(40), "Lee & Sons Café");
    for (String name : names) createEmployee(name);
    long runId = payWeek(names);
    String file =
        mvc.perform(get("/v1/pay-runs/" + runId + "/aba").with(officer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var payments = new AbaFile().parse(file).payments();
    assertThat(payments).hasSize(3);
    assertThat(payments)
        .extracting(AbaFile.Payment::name)
        .containsExactly("Renee Dubois", "A".repeat(32), "Lee Sons Cafe");
  }

  @Test
  void rejectsAnUnconvertibleNameWithEmployeeIdOnly() throws Exception {
    String name = "陈静";
    long employeeId = createEmployee(name);
    long runId = payWeek(List.of(name));
    mvc.perform(get("/v1/pay-runs/" + runId + "/aba").with(officer()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(containsString("Employee " + employeeId)))
        .andExpect(jsonPath("$.detail").value(not(containsString(name))));
  }

  private long createEmployee(String name) throws Exception {
    String response =
        mvc.perform(
                post("/v1/employees")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        new JsonMapper()
                            .writeValueAsString(
                                Map.of(
                                    "name", name,
                                    "employment", "PERMANENT",
                                    "hourlyRate", 20,
                                    "bsb", "123-456",
                                    "account", "12345678"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new JsonMapper().readTree(response).path("id").asLong();
  }

  private long payWeek(List<String> names) throws Exception {
    var csv = new StringBuilder("employee,date,start,end\n");
    for (String name : names) csv.append(name).append(",2026-07-06,09:00,17:00\n");
    var upload =
        new MockMultipartFile(
            "file", "timesheet.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/v1/timesheets").file(upload).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows").value(names.size()));
    String response =
        mvc.perform(
                post("/v1/pay-runs")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"periodStart\":\"2026-07-06\",\"periodEnd\":\"2026-07-12\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = new JsonMapper().readTree(response).path("id").asLong();
    for (String event : List.of("calculate", "approve", "pay"))
      mvc.perform(post("/v1/pay-runs/" + runId + "/" + event).with(officer()))
          .andExpect(status().isOk());
    return runId;
  }

  private static RequestPostProcessor officer() {
    return jwt()
        .jwt(token -> token.claim("employer_id", "aba-account-titles"))
        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"));
  }
}
