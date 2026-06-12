[English](README.md) | 简体中文

# Payslate

Payslate 原为中小企业提供薪资软件。Payslate Online 是基于 2004 年遗留薪资引擎建设的托管服务。目前的模块是 `pay-engine` 和 `bank-file`；后续将增加用于导入工时表、运行薪资批次和下载 ABA 文件的 Web API 与数据库。本仓库仅使用合成数据。测试 BSB 只检查字段格式，生成的文件不能提交给银行。

## 构建和运行

需要 Java 21。运行 `./mvnw -B -ntp verify`。`docker` profile 留给容器集成测试。演示 CLI：

```sh
./mvnw -q -pl pay-engine package
java -cp pay-engine/target/classes com.elsworne.payslate.engine.PayCli pay-engine/demo-timesheet.csv 20 PERMANENT
```

预期输出：

```text
Gross: 20.00
Tax: 0.00
Net: 20.00
```

`pay-engine/src/main/resources/example-tax-schedule.csv` 是**测试用示例**，并非官方税表。生产环境须每财年加载官方表。CLI 的 CSV 格式刻意保持简单；排班软件格式的 spike 将研究产品导入器。

覆盖率：`./mvnw -B -ntp verify` 生成 `pay-engine/target/site/jacoco/index.html` 和 `jacoco.xml`；pay-engine 分支门槛为 65%。参见 `docs/test-design/overtime-control-flow.zh-CN.md`。

变异测试：`./mvnw -B -ntp -pl pay-engine test-compile org.pitest:pitest-maven:mutationCoverage` 在 `pay-engine/target/pit-reports/` 写入 HTML 和 XML。实测基线见 `docs/quality/mutation-report.zh-CN.md`。

## Payslate Online API

使用 `./mvnw -B -ntp verify` 构建。运行本地 PostgreSQL 时，先设置本地开发用的 `PAYSLATE_DB_USER`、`PAYSLATE_DB_PASSWORD`、`PAYSLATE_DEV_ISSUER_ADMIN` 和 `PAYSLATE_DEV_ISSUER_PASSWORD`，执行 `./mvnw -pl payslate-api -am package`，再执行 `docker compose up --build`。API 监听宿主机的 8080 端口，可以用 `PAYSLATE_API_PORT` 改用其他端口。`payslate-api/openapi.yaml` 描述员工、CSV 工时表、工资批次、工资单与 ABA 下载。存活与就绪端点为 `/actuator/health/liveness` 和 `/actuator/health/readiness`。错误采用 `application/problem+json`，日志采用 ECS 结构化 JSON。`./mvnw -B -ntp -Pdocker verify` 也运行 PostgreSQL Testcontainers 测试。CSV 支持逗号、分号和制表符、ADR 0003 规定的表头别名、ISO 和澳大利亚日期、24 小时与 12 小时时间、UTF-8 BOM 以及带引号的姓名；列数混杂、非法日期和空班次会被拒绝。所有测试数据均为合成数据；仓库税表仅为示例，生产环境每财年必须加载官方税表。

本地 Compose 启动位于 `http://localhost:8081/realms/payslate` 的开发 OIDC 发行者，`PAYSLATE_ISSUER_PORT` 可以改它的宿主机端口。合成员工账户为 `officer-a`（雇主 `synthetic-a`）、`officer-b`（雇主 `synthetic-b`）及 `employee-a`；只用于本地测试的密码写在 `payslate-api/dev-issuer/payslate-realm.json`。可以通过 OIDC authorization-code 流程登录，也可以在开发 realm 使用 direct grant 取得 API 实验用 bearer token。token 必须包含 `employer_id` 和 `PAYROLL_OFFICER` 或 `EMPLOYEE` realm role。操作员只管理自己雇主的员工与工资批次；员工只能读取自己的工资单。所有数据库查询均按雇主 ID 限定。

发行者比 API 启动得慢：先等 `http://localhost:8081/realms/payslate/.well-known/openid-configuration` 能够返回，再申请 token。下面的命令从开发发行者取得 `officer-a` 的 token（若设置了 `PAYSLATE_ISSUER_PORT`，请相应改端口），响应中的 `access_token` 就是 bearer token：

```sh
curl -s http://localhost:8081/realms/payslate/protocol/openid-connect/token \
  -d grant_type=password -d client_id=payslate-ui -d username=officer-a -d password=dev-only-a
```

创建工资批次时提交 JSON `{"periodStart":"2026-02-23","periodEnd":"2026-03-01"}`；只有完整落在这组含首尾日期的区间内的班次会被计算。CSV 可包含 `public holiday` 列，接受 `true`/`false`、`yes`/`no` 或 `1`/`0`，缺省为 false。创建员工时可填固定 `deductions` 金额，缺省为零。扣款超过总工资减税时，整个批次会被拒绝，错误会指出员工。

## 关于本仓库

Elsworne、Payslate 和团队成员均为虚构。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
