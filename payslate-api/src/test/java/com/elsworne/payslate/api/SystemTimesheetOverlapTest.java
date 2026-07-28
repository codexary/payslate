package com.elsworne.payslate.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemTimesheetOverlapTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void withinFileOverlapReturns400() throws Exception {
    createEmployee("Liam Walsh");
    var csv = csv("Liam Walsh,2026-02-23,09:00,17:00\nLiam Walsh,2026-02-23,12:00,15:00\n");

    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString("Line 3")))
        .andExpect(jsonPath("$.detail", containsString("overlaps an earlier shift in the file")));
  }

  @Test
  void storedOverlapReturns409() throws Exception {
    createEmployee("Liam Walsh");
    var original = csv("Liam Walsh,2026-02-23,09:00,17:00\n");
    mvc.perform(multipart("/v1/timesheets").file(original).with(officer()))
        .andExpect(status().isOk());
    var overlapping = csv("Liam Walsh,2026-02-23,12:00,15:00\n");

    mvc.perform(multipart("/v1/timesheets").file(overlapping).with(officer()))
        .andExpect(status().isConflict())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString("Line 2")))
        .andExpect(jsonPath("$.detail", containsString("overlaps an existing shift")));
  }

  @Test
  void sameFileTwiceReturns409() throws Exception {
    createEmployee("Liam Walsh");
    var csv = csv("Liam Walsh,2026-02-23,09:00,17:00\n");
    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());

    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer()))
        .andExpect(status().isConflict())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString("Line 2")))
        .andExpect(jsonPath("$.detail", containsString("overlaps an existing shift")));
  }

  @Test
  void adjacentShiftsAccepted() throws Exception {
    createEmployee("Liam Walsh");
    var csv = csv("Liam Walsh,2026-02-23,09:00,17:00\nLiam Walsh,2026-02-23,17:00,21:00\n");

    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());
  }

  @Test
  void sameTimeDifferentEmployeesAccepted() throws Exception {
    createEmployee("Liam Walsh");
    createEmployee("Mia Chen");
    var csv = csv("Liam Walsh,2026-02-23,09:00,17:00\nMia Chen,2026-02-23,09:00,17:00\n");

    mvc.perform(multipart("/v1/timesheets").file(csv).with(officer())).andExpect(status().isOk());
  }

  private long createEmployee(String name) throws Exception {
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
    return id(employee.getResponse().getContentAsString());
  }

  private static MockMultipartFile csv(String rows) {
    return new MockMultipartFile(
        "file",
        "timesheet.csv",
        "text/csv",
        ("employee,date,start,end\n" + rows).getBytes(StandardCharsets.UTF_8));
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
