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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SystemWholePayrollFlowTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

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
            "employee,date,start,end,public holiday\nAlice Test,2026-02-23,09:00,17:00,true\nAlice Test,2026-03-02,09:00,17:00,false\n"
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
    var batch = new AbaFile().parse(aba);
    assertThat(batch.payments()).hasSize(1);
    assertThat(batch.payments().get(0).name()).isEqualTo("Alice Test");
    assertThat(batch.payments().get(0).cents()).isEqualTo(36_000L);
    String trailer = aba.split("\r\n")[3];
    assertThat(trailer.substring(20, 30)).isEqualTo("0000000000");
    assertThat(trailer.substring(30, 40)).isEqualTo("0000036000");
    assertThat(trailer.substring(40, 50)).isEqualTo("0000036000");
  }

  @Test
  void excessiveDeductionsRejectEntirePayRunAndNameEmployee() throws Exception {
    mvc.perform(
            post("/v1/employees")
                .with(officer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Excess Test\",\"employment\":\"PERMANENT\",\"hourlyRate\":20,\"bsb\":\"123-456\",\"account\":\"12345678\",\"deductions\":1000}"))
        .andExpect(status().isOk());
    var csv =
        new MockMultipartFile(
            "file",
            "excess.csv",
            "text/csv",
            "employee,date,start,end\nExcess Test,2026-03-09,09:00,10:00\n"
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());
    var run =
        mvc.perform(
                post("/v1/pay-runs")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"periodStart\":\"2026-03-09\",\"periodEnd\":\"2026-03-15\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long runId = id(run.getResponse().getContentAsString());
    var rejected =
        mvc.perform(post("/v1/pay-runs/" + runId + "/calculate").with(officer()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(rejected.getResponse().getContentAsString()).contains("Excess Test");
    var state =
        mvc.perform(get("/v1/pay-runs/" + runId).with(officer()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(state.getResponse().getContentAsString()).contains("DRAFT");
  }

  @Test
  void calendarEndpointBuildsStoredPeriodFromPayCalendar() throws Exception {
    var created =
        mvc.perform(
                post("/v1/pay-runs/calendar")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"frequency\":\"WEEKLY\",\"anchorPayDate\":\"2026-03-01\",\"payDate\":\"2026-03-01\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long runId = id(created.getResponse().getContentAsString());
    var dates =
        jdbc.queryForMap("SELECT period_start,period_end FROM pay_runs WHERE id = ?", runId);
    assertThat(((java.sql.Date) dates.get("period_start")).toLocalDate())
        .isEqualTo(java.time.LocalDate.of(2026, 2, 23));
    assertThat(((java.sql.Date) dates.get("period_end")).toLocalDate())
        .isEqualTo(java.time.LocalDate.of(2026, 3, 1));
    mvc.perform(
            post("/v1/pay-runs/calendar")
                .with(officer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"frequency\":\"WEEKLY\",\"anchorPayDate\":\"2026-03-01\",\"payDate\":\"2026-03-02\"}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({"2023-04-02,270.00", "2023-10-01,210.00"})
  void uploadedTransitionShiftPaysElapsedTimeInDefaultZone(String date, String gross)
      throws Exception {
    String name = "DST " + date;
    var employee =
        mvc.perform(
                post("/v1/employees")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
            {"name":"%s","employment":"PERMANENT","hourlyRate":20,
             "bsb":"123-456","account":"12345678"}
            """
                            .formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
    long employeeId = id(employee.getResponse().getContentAsString());
    var csv =
        new MockMultipartFile(
            "file",
            "transition.csv",
            "text/csv",
            ("employee,date,start,end\n" + name + "," + date + ",01:00,09:00\n")
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());
    var run =
        mvc.perform(
                post("/v1/pay-runs/calendar")
                    .with(officer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
            {"frequency":"WEEKLY","anchorPayDate":"%s","payDate":"%s"}
            """
                            .formatted(date, date)))
            .andExpect(status().isOk())
            .andReturn();
    long runId = id(run.getResponse().getContentAsString());
    mvc.perform(post("/v1/pay-runs/" + runId + "/calculate").with(officer()))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT gross FROM payslips WHERE run_id = ? AND employee_id = ?",
                java.math.BigDecimal.class,
                runId,
                employeeId))
        .isEqualByComparingTo(gross);
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
