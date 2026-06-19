[English](0010-layered-payroll-integration.md) | 简体中文

# ADR 0010：按接缝验证在线工资流程与排班契约

Ravi Menon — 2026-06-19

## 状态

2026-06-19 采纳。

## 背景

Payslate Online 把已测试的工资引擎和 ABA 写入器接到 Spring Boot API、Flyway 与 PostgreSQL 上。第一版端到端流程只服务一个雇主，计算时取全部已保存的班次。Margaret O'Connell 评审时问了两件事：工资职员是否只能处理本雇主的工资？一个工资批次是否只包含本期班次？现在，职员的 JWT 带 `employer_id` 以及 `PAYROLL_OFFICER` 或 `EMPLOYEE` 角色，员工只能读本人的工资单；工资批次带起止日期，计算时按雇主和期间筛选班次。默认 `./mvnw -B -ntp clean verify` 在 pay-engine、bank-file 和 API 三个模块分别运行 80、16、8 项测试，共 104 项；`-Pdocker verify` 为 80、16、10 项，共 106 项。

第一次 PostgreSQL 集成测试在 `PayrollService.insert` 读取生成键时失败：宽泛的生成键请求返回了新行的所有列，而 `GeneratedKeyHolder.getKey()` 只接受一列。H2 上的系统流程此前已经通过。改为只请求 `id` 后问题解决。系统测试现在检查一笔 36,000 分的公共假日付款、ABA 尾记录合计、按期间筛选班次，以及扣款过多时批次仍停在 `DRAFT`。另有一项 PostgreSQL 集成测试检查雇主隔离和角色边界。

[排班契约原型](https://github.com/codexary/payslate-spike-contract)用 Pact mock server 检查 Payslate 这一侧的消费者代码，再用本地 stub provider 验证已提交的 pact，尚未接触第三方的线上服务。

## 决定

默认构建保留 CSV、规则、MockMvc 与 H2 的快速反馈；PostgreSQL 的生成键和雇主隔离检查放在 Docker profile 中，CI 两个 profile 都运行。集成按依赖顺序进行：先计算器和导入器，再接真实数据库，最后是 MVC 与从上传到 ABA 的整条流程。Margaret 的批准规则和冲正规则保留为具名的验收场景。

每条工资数据查询都按 JWT 中的 `employer_id` 限定；员工读工资单时，还要求与 token 中的 `employee_id` 一致。创建批次时确定工资期，只选取期间内的班次。扣款过多时拒绝整次计算，批次留在 `DRAFT`。

产品接入排班服务之前，先用消费者 pact 固定请求和必需的响应字段。只有同一份 pact 在真实 provider 的发布版本上验证通过，才宣称双方兼容。

## 考虑过的方案

- 只用 H2 跑整条流程：它通过了，而 PostgreSQL 上的生成键处理却失败。
- 所有测试都放到 Docker 后面：CSV、规则和 MVC 的反馈不需要容器。
- 只检查职员角色：有这个角色的人仍可能猜到别家雇主的记录 ID。
- 把本地 Pact stub 当作第三方验证：这个 stub 由 Payslate 自己控制。

## 后果

默认作业和 Docker 作业会重复运行一部分测试，但数据库特有的失败能在很窄的范围内复现。H2 系统流程检查经过身份认证的上传到 ABA 路径、金额和失败后的状态；PostgreSQL 测试检查生成键和雇主隔离，不覆盖整条 ABA 路径。跨期班次如何拆分、同名员工的稳定标识、在真实第三方服务上的验证，仍是待定的决定。
