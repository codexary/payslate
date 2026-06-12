package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elsworne.payslate.bank.AbaFile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SystemWholePayrollFlowTest {
  @Autowired MockMvc mvc;

  @Test
  void uploadCalculateApprovePayAndParseAba() throws Exception {
    var employee =
        mvc.perform(
                post("/v1/employees")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Alice Test\",\"employment\":\"PERMANENT\",\"hourlyRate\":20,\"bsb\":\"123-456\",\"account\":\"12345678\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long employeeId = id(employee.getResponse().getContentAsString());
    var csv =
        new MockMultipartFile(
            "file",
            "timesheet.csv",
            "text/csv",
            "employee,date,start,end\nAlice Test,2026-02-23,09:00,17:00\n"
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());
    var run =
        mvc.perform(
                post("/v1/pay-runs")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"periodStart\":\"2026-02-23\",\"periodEnd\":\"2026-03-01\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long runId = id(run.getResponse().getContentAsString());
    for (String event : new String[] {"calculate", "approve", "pay"})
      mvc.perform(post("/v1/pay-runs/" + runId + "/" + event).with(officer()))
          .andExpect(status().isOk());
    mvc.perform(post("/v1/pay-runs/" + runId + "/approve").with(officer()))
        .andExpect(status().isConflict());
    mvc.perform(get("/v1/pay-runs/" + runId + "/payslips/" + employeeId).with(officer()))
        .andExpect(status().isOk());
    var aba =
        mvc.perform(get("/v1/pay-runs/" + runId + "/aba").with(officer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(new AbaFile().parse(aba).payments()).hasSize(1);
    assertThat(new AbaFile().parse(aba).payments().get(0).name()).isEqualTo("Alice Test");
  }

  private static RequestPostProcessor officer() {
    return jwt()
        .jwt(token -> token.claim("employer_id", "test-employer"))
        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"));
  }

  private static long id(String json) {
    return Long.parseLong(json.replaceAll("[^0-9]", ""));
  }
}
