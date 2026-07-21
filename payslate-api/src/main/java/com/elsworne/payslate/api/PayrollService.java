package com.elsworne.payslate.api;

import com.elsworne.payslate.bank.AbaFile;
import com.elsworne.payslate.engine.LegacyPayEngine;
import com.elsworne.payslate.engine.PayCalendar;
import com.elsworne.payslate.engine.PayEngine;
import com.elsworne.payslate.engine.PayRejectedException;
import com.elsworne.payslate.engine.payrun.PayRun;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollService {
  private final JdbcTemplate jdbc;
  private final PayEngine engine;
  private final AbaFile aba = new AbaFile();

  PayrollService(
      JdbcTemplate jdbc, @Value("${payslate.time-zone:Australia/Melbourne}") String timeZone) {
    this.jdbc = jdbc;
    this.engine = new PayEngine(ZoneId.of(timeZone));
  }

  @Transactional
  public long createEmployee(
      String employer,
      String name,
      String employment,
      BigDecimal rate,
      String bsb,
      String account,
      BigDecimal deductions) {
    if (employer == null
        || employer.isBlank()
        || name == null
        || name.isBlank()
        || rate == null
        || rate.signum() < 0
        || employment == null
        || !List.of("PERMANENT", "CASUAL").contains(employment)
        || bsb == null
        || !bsb.matches("[0-9]{3}-[0-9]{3}")
        || account == null
        || !account.matches("[0-9]{1,9}")
        || deductions == null
        || deductions.signum() < 0) throw new InvalidRequestException("Invalid employee");
    return insert(
        "INSERT INTO employees(employer_id,name,employment,hourly_rate,bsb,account,deductions) VALUES(?,?,?,?,?,?,?)",
        employer,
        name,
        employment,
        rate,
        bsb,
        account,
        deductions);
  }

  @Transactional
  public int upload(String employer, String csv) {
    return uploadRows(employer, TimesheetCsv.parse(csv));
  }

  @Transactional
  public int upload(String employer, java.io.InputStream csv) {
    return uploadRows(employer, TimesheetCsv.parse(csv));
  }

  private int uploadRows(String employer, List<TimesheetCsv.Row> rows) {
    for (var row : rows) {
      var ids =
          jdbc.queryForList(
              "SELECT id FROM employees WHERE employer_id = ? AND name = ?",
              Long.class,
              employer,
              row.employee());
      if (ids.size() != 1)
        throw new InvalidRequestException(
            "Line " + row.line() + ": employee name must match exactly one employee");
      jdbc.update(
          "INSERT INTO timesheets(employer_id,employee_id,starts,ends,public_holiday) VALUES(?,?,?,?,?)",
          employer,
          ids.get(0),
          row.start(),
          row.end(),
          row.publicHoliday());
    }
    return rows.size();
  }

  public Map<String, Object> employee(String employer, long id) {
    var employees =
        jdbc.queryForList(
            "SELECT id,name,employment,hourly_rate FROM employees WHERE employer_id = ? AND id = ?",
            employer,
            id);
    if (employees.isEmpty()) throw new RecordNotFoundException();
    return employees.get(0);
  }

  public List<Map<String, Object>> searchEmployees(String employer, String searchText) {
    if (searchText == null || searchText.isBlank())
      throw new InvalidRequestException("Missing search text");
    String sql =
        "SELECT id,name,hourly_rate FROM employees WHERE employer_id = ? AND name LIKE ? ORDER BY id";
    return jdbc.queryForList(sql, employer, "%" + searchText + "%");
  }

  @Transactional
  public long createRun(String employer, LocalDate start, LocalDate end) {
    if (employer == null
        || employer.isBlank()
        || start == null
        || end == null
        || end.isBefore(start)) throw new InvalidRequestException("Invalid pay period");
    return insert(
        "INSERT INTO pay_runs(employer_id,state,created_at,period_start,period_end) VALUES(?,?,?,?,?)",
        employer,
        "DRAFT",
        LocalDateTime.now(),
        start,
        end);
  }

  @Transactional
  public long createCalendarRun(
      String employer,
      PayCalendar.Frequency frequency,
      LocalDate anchorPayDate,
      LocalDate payDate) {
    if (payDate == null
        || frequency == null
        || anchorPayDate == null
        || payDate.getYear() < 1
        || payDate.getYear() > 9999) throw new InvalidRequestException("Invalid calendar input");
    var period =
        PayCalendar.periodsForYear(frequency, anchorPayDate, payDate.getYear()).stream()
            .filter(candidate -> candidate.payDate().equals(payDate))
            .findFirst()
            .orElseThrow(() -> new InvalidRequestException("Pay date is not on the calendar"));
    return createRun(employer, period.startInclusive(), period.endExclusive().minusDays(1));
  }

  @Transactional
  public String transition(String employer, long id, PayRun.Event event) {
    String current = state(employer, id);
    var model = PayRun.restore(storedEnum(PayRun.State.class, current));
    try {
      model.apply(event);
    } catch (IllegalStateException error) {
      throw new PayRunConflictException();
    }
    if (event == PayRun.Event.CALCULATE) calculate(employer, id);
    jdbc.update(
        "UPDATE pay_runs SET state = ? WHERE id = ? AND employer_id = ?",
        model.state().name(),
        id,
        employer);
    return model.state().name();
  }

  public String state(String employer, long id) {
    var states =
        jdbc.queryForList(
            "SELECT state FROM pay_runs WHERE id = ? AND employer_id = ?",
            String.class,
            id,
            employer);
    if (states.isEmpty()) throw new RecordNotFoundException();
    return states.get(0);
  }

  private void calculate(String employer, long id) {
    var periods =
        jdbc.queryForList(
            "SELECT period_start,period_end FROM pay_runs WHERE id = ? AND employer_id = ?",
            id,
            employer);
    if (periods.size() != 1) throw new RecordNotFoundException();
    LocalDate start = ((java.sql.Date) periods.get(0).get("period_start")).toLocalDate();
    LocalDate end = ((java.sql.Date) periods.get(0).get("period_end")).toLocalDate();
    var employees =
        jdbc.queryForList(
            "SELECT id,name,employment,hourly_rate,deductions FROM employees WHERE employer_id = ?",
            employer);
    for (Map<String, Object> employee : employees) {
      long employeeId = ((Number) employee.get("id")).longValue();
      var shifts =
          jdbc.query(
              "SELECT starts,ends,public_holiday FROM timesheets WHERE employer_id = ? AND employee_id = ? AND starts >= ? AND ends <= ? ORDER BY starts",
              (rs, n) ->
                  new LegacyPayEngine.Shift(
                      rs.getTimestamp(1).toLocalDateTime(),
                      rs.getTimestamp(2).toLocalDateTime(),
                      rs.getBoolean(3)),
              employer,
              employeeId,
              start.atStartOfDay(),
              end.plusDays(1).atStartOfDay());
      if (shifts.isEmpty()) continue;
      PayEngine.Result result;
      try {
        result =
            engine.calculate(
                (String) employee.get("name"),
                (BigDecimal) employee.get("hourly_rate"),
                storedEnum(LegacyPayEngine.Employment.class, (String) employee.get("employment")),
                shifts,
                (BigDecimal) employee.get("deductions"));
      } catch (PayRejectedException error) {
        throw new InvalidRequestException(error.getMessage(), error);
      }
      jdbc.update(
          "INSERT INTO payslips(employer_id,run_id,employee_id,gross,tax,net) VALUES(?,?,?,?,?,?)",
          employer,
          id,
          employeeId,
          result.gross(),
          result.tax(),
          result.net());
    }
  }

  public Map<String, Object> payslip(String employer, long runId, long employeeId) {
    state(employer, runId);
    var slips =
        jdbc.queryForList(
            "SELECT gross,tax,net FROM payslips WHERE employer_id = ? AND run_id = ? AND employee_id = ?",
            employer,
            runId,
            employeeId);
    if (slips.isEmpty()) throw new RecordNotFoundException();
    return slips.get(0);
  }

  public String aba(String employer, long runId) {
    if (!state(employer, runId).equals("PAID")) throw new PayRunConflictException();
    var payments = new ArrayList<AbaFile.Payment>();
    jdbc.query(
        "SELECT e.bsb,e.account,e.name,p.net FROM payslips p JOIN employees e ON e.id=p.employee_id AND e.employer_id=p.employer_id WHERE p.employer_id=? AND p.run_id=? ORDER BY e.id",
        rs -> {
          long cents = rs.getBigDecimal(4).movePointRight(2).longValueExact();
          if (cents > 0)
            payments.add(
                new AbaFile.Payment(
                    rs.getString(1), rs.getString(2), rs.getString(3), "PAY " + runId, cents));
        },
        employer,
        runId);
    return aba.write(
        new AbaFile.Batch(
            "TST",
            "ELSWORNE",
            "123456",
            "PAYROLL",
            LocalDate.now(),
            "123-456",
            "87654321",
            payments));
  }

  private static <E extends Enum<E>> E storedEnum(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException error) {
      throw new IllegalStateException("Invalid stored payroll state", error);
    }
  }

  private long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
          return ps;
        },
        key);
    return key.getKey().longValue();
  }
}
