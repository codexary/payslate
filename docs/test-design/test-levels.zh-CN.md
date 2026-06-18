[English](test-levels.md) | 简体中文

# 测试层级与集成顺序

| 层级 | 测试 | 能发现的问题 |
| --- | --- | --- |
| 单元 | `UnitTimesheetCsvTest`、状态和计算测试 | CSV 输入分区、状态迁移与计算分支 |
| API | `ApiOpenApiContractTest` | OpenAPI 路径与运行中的 Spring 映射不一致 |
| 集成 | `IntegrationPostgresMigrationTest`（`docker`） | PostgreSQL 中 Flyway 或 JDBC 行为错误 |
| 系统 | `SystemWholePayrollFlowTest` | CSV 上传、计算、批准、支付、ABA 下载与解析的完整链路 |
| 验收 | `AcceptanceMargaretPayrollTest` | Margaret 确认的支付前必须批准、支付后才能冲正规则 |

集成顺序：先计算器与导入器，再用真实 PostgreSQL 容器检查 JDBC，最后检查 MVC 和完整流程。系统测试以 H2 作为数据库替身，因此无需 Docker 也能运行 `verify`；Docker 集成测试补查数据库接缝。Pact 实验为消费者提供排班服务的 stub provider。仓库不能访问真实第三方服务。

本地 PostgreSQL 集成测试使用两个不同 `employer_id` 的 JWT 主体，检查另一个雇主不能读取员工、批次或工资单。系统测试同时核对公共假日金额、工资期过滤和 ABA 分金额及总计；OpenAPI 测试逐一检查请求与响应 schema。
