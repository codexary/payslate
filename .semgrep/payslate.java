// Rule test for payslate.dynamic-jdbc-sql. Run with `semgrep --test`.
// A query built only from string literals is a constant and must not be
// flagged, however many literals are joined; once any operand is a variable
// the whole concatenation must be flagged, wherever the variable sits.
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DynamicJdbcSqlTest {
  void samples(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, String input, Map<String, ?> args) {
    // Two literals joined: the real PayrollService case. Constant, so ok.
    // ok: payslate.dynamic-jdbc-sql
    named.queryForList("SELECT id FROM employees WHERE employer_id = :employer AND id IN (:ids)" + " ORDER BY id FOR UPDATE", args, Long.class);

    // Three literals joined, still constant: ok.
    // ok: payslate.dynamic-jdbc-sql
    jdbc.queryForList("SELECT a" + " FROM t" + " WHERE b = 1", Long.class);

    // ok: payslate.dynamic-jdbc-sql
    jdbc.queryForObject("SELECT COUNT(*) FROM t" + " WHERE active = true", Long.class);

    // ok: payslate.dynamic-jdbc-sql
    jdbc.update("UPDATE t SET a = 1" + " WHERE id = 2");

    // Variable mixed in: must be flagged wherever it sits.
    // ruleid: payslate.dynamic-jdbc-sql
    jdbc.queryForList("SELECT * FROM employees WHERE name = '" + input + "'", Long.class);

    // Variable in the middle of three operands.
    // ruleid: payslate.dynamic-jdbc-sql
    jdbc.queryForList("SELECT a FROM t WHERE b = " + input + " ORDER BY id", Long.class);

    // Variable at the end, after two literals.
    // ruleid: payslate.dynamic-jdbc-sql
    jdbc.queryForObject("SELECT COUNT(*) FROM t" + " WHERE id = " + input, Long.class);

    // Built-up String variable then used in a query.
    // ruleid: payslate.dynamic-jdbc-sql
    String sql = "SELECT * FROM t WHERE x = " + input + " ORDER BY id";
    jdbc.query(sql, (rs, n) -> null);
  }
}
