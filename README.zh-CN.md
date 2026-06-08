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

使用 `./mvnw -B -ntp verify` 构建。运行本地 PostgreSQL 时，先将 `PAYSLATE_DB_USER`、`PAYSLATE_DB_PASSWORD` 设置为非默认值，执行 `./mvnw -pl payslate-api -am package`，再执行 `docker compose up --build`。API 监听宿主机的 8080 端口，可以用 `PAYSLATE_API_PORT` 改用其他端口。`payslate-api/openapi.yaml` 描述员工、CSV 工时表、工资批次、工资单与 ABA 下载。存活与就绪端点为 `/actuator/health/liveness` 和 `/actuator/health/readiness`。错误采用 `application/problem+json`，日志采用 ECS 结构化 JSON。`./mvnw -B -ntp -Pdocker verify` 也运行 PostgreSQL Testcontainers 测试。CSV 支持逗号、分号和制表符、ADR 0003 规定的表头别名、ISO 和澳大利亚日期、24 小时与 12 小时时间、UTF-8 BOM 以及带引号的姓名；列数混杂、非法日期和空班次会被拒绝。所有测试数据均为合成数据；仓库税表仅为示例，生产环境每财年必须加载官方税表。

## 关于本仓库

Elsworne、Payslate 和团队成员均为虚构。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
