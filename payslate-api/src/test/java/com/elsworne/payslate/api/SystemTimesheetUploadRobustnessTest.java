package com.elsworne.payslate.api;

import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.HEADER;
import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.LINE_TWO;
import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.QUOTED_FIELD;
import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.QUOTED_LINE_BREAK;
import static com.elsworne.payslate.api.UnitTimesheetCsvRobustnessTest.QUOTED_ROWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mvc.problemdetails.enabled=true")
@AutoConfigureMockMvc
class SystemTimesheetUploadRobustnessTest {
  static final String NOT_UTF8 = "not UTF-8";
  private String employer;

  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired PayrollService service;
  @MockitoBean JwtDecoder decoder;

  @BeforeEach
  void authenticate() {
    employer = "upload-regression-" + UUID.randomUUID();
    UploadHttpSupport.authenticate(decoder, employer);
  }

  @Test
  void acceptsFullyQuotedFieldsAcrossMultipleRows() throws Exception {
    employee("Liam Walsh");
    employee("Renée Dubois");
    var response = UploadHttpSupport.upload(port, QUOTED_ROWS);
    assertEquals(200, response.statusCode(), "HTTP status");
    assertThat(response.body()).contains("\"rows\":2");
  }

  @Test
  void rejectsQuotedLineBreakWithTheRecordStartLine() throws Exception {
    var response =
        UploadHttpSupport.upload(port, HEADER + "\"Liam\nWalsh\",2026-07-06,09:00,17:00\n");
    assertThat(UploadHttpSupport.problem(response, 400).path("detail").asString())
        .contains(LINE_TWO, QUOTED_LINE_BREAK);
  }

  @ParameterizedTest
  @MethodSource("brokenQuotes")
  void rejectsBrokenQuotingAsBadRequest(int line, String csv) throws Exception {
    var response = UploadHttpSupport.upload(port, csv);
    assertThat(UploadHttpSupport.problem(response, 400).path("detail").asString())
        .contains("Line " + line, QUOTED_FIELD);
  }

  static Stream<Arguments> brokenQuotes() {
    return Stream.of(
        Arguments.of(1, "\""), // The campaign reduced an unclosed field to a single quote.
        Arguments.of(2, HEADER + "\"Liam Walsh,2026-07-06,09:00,17:00"),
        Arguments.of(2, HEADER + "\"Liam Walsh\"x,2026-07-06,09:00,17:00"));
  }

  @Test
  void rejectsWindows1252InsteadOfReplacingInvalidBytes() throws Exception {
    employee("Renée Dubois");
    byte[] bytes =
        (HEADER + "Renée Dubois,2026-07-07,09:00,17:00\n")
            .getBytes(Charset.forName("windows-1252"));
    var file = new MockMultipartFile("file", "timesheet.csv", "text/csv", bytes);
    mvc.perform(
            multipart("/v1/timesheets")
                .file(file)
                .with(
                    jwt()
                        .jwt(token -> token.claim("employer_id", employer))
                        .authorities(new SimpleGrantedAuthority("ROLE_PAYROLL_OFFICER"))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(containsString(NOT_UTF8)));
  }

  @Test
  void rejectsMoreThanTenThousandDataRowsAsBadRequest() throws Exception {
    // Ensure employee lookup succeeds, so a missing employee cannot masquerade as the row limit.
    employee("Mia Chen");
    var response =
        UploadHttpSupport.upload(port, HEADER + "Mia Chen,2026-07-07,09:00,17:00\n".repeat(10_001));
    UploadHttpSupport.problem(response, 400);
  }

  @Test
  void stillRejectsUploadsLargerThanOneMegabyteWithProblemDetails() throws Exception {
    // Real multipart parsing is necessary: MockMvc multipart bypasses the servlet size limit.
    var response = UploadHttpSupport.upload(port, "x".repeat(1024 * 1024 + 1));
    UploadHttpSupport.problem(response, 413);
  }

  private void employee(String name) {
    // Separate employers avoid any dependency on method order or other test classes.
    service.createEmployee(
        employer, name, "PERMANENT", new BigDecimal("20"), "123-456", "12345678", BigDecimal.ZERO);
  }
}
