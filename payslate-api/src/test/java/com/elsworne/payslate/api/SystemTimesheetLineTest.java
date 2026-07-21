package com.elsworne.payslate.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.mvc.problemdetails.enabled=true")
@AutoConfigureMockMvc
class SystemTimesheetLineTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void unknownEmployeeNamesThePhysicalLine() throws Exception {
    var file =
        new MockMultipartFile(
            "file",
            "timesheet.csv",
            "text/csv",
            ("employee,date,start,end\n\nUnknown employee,2026-07-20,09:00,17:00\n")
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/v1/timesheets")
                .file(file)
                .with(
                    jwt()
                        .jwt(token -> token.claim("employer_id", "line-test"))
                        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("Line 3")));
  }
}
