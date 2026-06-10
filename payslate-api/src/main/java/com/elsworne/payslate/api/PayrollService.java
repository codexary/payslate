package com.elsworne.payslate.api;

import com.elsworne.payslate.bank.AbaFile;
import com.elsworne.payslate.engine.LegacyPayEngine;
import com.elsworne.payslate.engine.PayEngine;
import com.elsworne.payslate.engine.payrun.PayRun;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollService {
  private final JdbcTemplate jdbc;
  private final PayEngine engine = new PayEngine();
  private final AbaFile aba = new AbaFile();

  PayrollService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public long createEmployee(
      String name, String employment, BigDecimal rate, String bsb, String account) {
    if (name == null
        || name.isBlank()
        || rate == null
        || rate.signum() < 0
        || !List.of("PERMANENT", "CASUAL").contains(employment)
        || bsb == null
        || !bsb.matches("[0-9]{3}-[0-9]{3}")
        || account == null
        || !account.matches("[0-9]{1,9}")) throw new IllegalArgumentException("Invalid employee");
    return insert(
        "INSERT INTO employees(name,employment,hourly_rate,bsb,account) VALUES(?,?,?,?,?)",
        name,
        employment,
        rate,
        bsb,
        account);
  }

  @Transactional
  public int upload(String csv) {
    var rows = TimesheetCsv.parse(csv);
    for (var row : rows) {
      var ids =
          jdbc.queryForList("SELECT id FROM employees WHERE name = ?", Long.class, row.employee());
      if (ids.size() != 1)
        throw new IllegalArgumentException("Employee name must match exactly one employee");
      jdbc.update(
          "INSERT INTO timesheets(employee_id,starts,ends,public_holiday) VALUES(?,?,?,?)",
          ids.get(0),
          row.start(),
          row.end(),
          false);
    }
    return rows.size();
  }

  @Transactional
  public long createRun() {
    return insert(
        "INSERT INTO pay_runs(state,created_at) VALUES(?,?)", "DRAFT", LocalDateTime.now());
  }

  @Transactional
  public String transition(long id, PayRun.Event event) {
    String current = state(id);
    var model = PayRun.restore(PayRun.State.valueOf(current));
    model.apply(event);
    if (event == PayRun.Event.CALCULATE) calculate(id);
    jdbc.update("UPDATE pay_runs SET state = ? WHERE id = ?", model.state().name(), id);
    return model.state().name();
  }

  public String state(long id) {
    var states = jdbc.queryForList("SELECT state FROM pay_runs WHERE id = ?", String.class, id);
    if (states.isEmpty()) throw new IllegalArgumentException("Unknown pay run");
    return states.get(0);
  }

  private void calculate(long id) {
    var employees = jdbc.queryForList("SELECT id,employment,hourly_rate FROM employees");
    for (Map<String, Object> employee : employees) {
      long employeeId = ((Number) employee.get("id")).longValue();
      var shifts =
          jdbc.query(
              "SELECT starts,ends,public_holiday FROM timesheets WHERE employee_id = ?",
              (rs, n) ->
                  new LegacyPayEngine.Shift(
                      rs.getTimestamp(1).toLocalDateTime(),
                      rs.getTimestamp(2).toLocalDateTime(),
                      rs.getBoolean(3)),
              employeeId);
      if (shifts.isEmpty()) continue;
      var result =
          engine.calculate(
              (BigDecimal) employee.get("hourly_rate"),
              LegacyPayEngine.Employment.valueOf((String) employee.get("employment")),
              shifts,
              BigDecimal.ZERO);
      jdbc.update(
          "INSERT INTO payslips(run_id,employee_id,gross,tax,net) VALUES(?,?,?,?,?)",
          id,
          employeeId,
          result.gross(),
          result.tax(),
          result.net());
    }
  }

  public Map<String, Object> payslip(long runId, long employeeId) {
    var slips =
        jdbc.queryForList(
            "SELECT gross,tax,net FROM payslips WHERE run_id = ? AND employee_id = ?",
            runId,
            employeeId);
    if (slips.isEmpty()) throw new IllegalArgumentException("Unknown payslip");
    return slips.get(0);
  }

  public String aba(long runId) {
    if (!state(runId).equals("PAID")) throw new IllegalStateException("Pay run is not paid");
    var payments = new ArrayList<AbaFile.Payment>();
    jdbc.query(
        "SELECT e.bsb,e.account,e.name,e.id,p.net FROM payslips p JOIN employees e ON e.id=p.employee_id WHERE p.run_id=? ORDER BY e.id",
        rs -> {
          long cents = rs.getBigDecimal(5).movePointRight(2).longValueExact();
          if (cents > 0)
            payments.add(
                new AbaFile.Payment(
                    rs.getString(1), rs.getString(2), rs.getString(3), "PAY " + runId, cents));
        },
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
