[English](0005-pay-engine-branch-coverage-gate.md) | 简体中文

# ADR 0005：为 pay-engine 设置 65% 分支覆盖门槛

Ravi Menon — 2026-04-10

## 状态

2026-04-10 采纳。

## 背景

边界测试已明确日加班和按日历周计算的周加班门槛，团队准备重构继承的奖项规则。Olivia 的控制流笔记 [docs/test-design/overtime-control-flow.md](../test-design/overtime-control-flow.md) 说明现有测试已执行带编号的 `isOvertime` 实现中的五个源语句和四个判定出口。JaCoCo 对该方法报告 LINE 3/3、INSTRUCTION 12/12、BRANCH 4/4。这个局部结果不能代表整个 `pay-engine`。

一次 `./mvnw -B -ntp clean verify` 的实测结果显示，`pay-engine/target/site/jacoco/jacoco.xml` 中的 `pay-engine` bundle 有 49 个已覆盖、19 个未覆盖分支，覆盖率为 72.06%。45 个 `pay-engine` 测试和 15 个 `bank-file` 测试全部通过；计数包含参数化测试的各次调用。

继承的 `overtimeFactor` 条件是从桌面版引擎的逐分钟循环中原样移入的。加入节假日回归测试并修复之前，该方法已覆盖 LINE 2/2 和 INSTRUCTION 12/12，但 BRANCH 只有 3/4。普通加班使 `factor < tier` 为真；`shiftCrossingSundayMidnightUsesNextWeeksThreshold` 使两个比较均为假。缺少的是 `factor > 2.0` 为真的结果。小时工资为 $10 的九小时公共假日班次应付 $202.50，实际却付了 $195.00。`holidayOvertimeKeepsHigherHolidayFactor` 在修复前失败，修复后通过。删除 `|| factor > 2.0` 后，较高的节假日倍率得以保留；修复后的方法达到 LINE 2/2、INSTRUCTION 8/8、BRANCH 2/2。行和指令完全覆盖并不能证明工资正确。

当前引擎按日历周累计加班分钟和倍率档，并用 `BigDecimal` 累加每分钟的倍率。

## 决定

为 `pay-engine` bundle 的 JaCoCo `BRANCH` `COVEREDRATIO` 设置最低值 `0.65`。拉取请求执行 `./mvnw -B -ntp verify`，同时运行测试、生成报告和检查门槛。评审奖项规则变更时，查看改动附近未覆盖的分支，并要求对改变的行为断言预期工资金额。门槛是底线，不是薪资正确的证明。

在 `pay-engine/pom.xml` 中保留 `prepare-agent` 和在 `verify` 阶段生成 HTML/XML 的 `report`，新增同样在 `verify` 阶段执行的 `jacoco:check`，其 execution 名为 `branch-gate`，规则范围为 `BUNDLE`。门槛只针对 `pay-engine`，不针对 `bank-file`。

## 考虑过的方案

- **不设门槛：**覆盖率下降可能没有明显信号。团队仍可审查测试，但失去机械检查的底线。
- **100% bundle 分支门槛：**继承的计算代码和 CLI 的防御路径会让不相关改动也依赖全部字节码出口。改写代码后，出口数可能变化，即使业务行为没有变化。
- **语句或行门槛：**继承的方法中，三个源语句和两行源码均已执行，节假日缺陷却仍然存在。这些计数器不会指出缺少的条件结果。

## 后果

低于 bundle 门槛的拉取请求会在验证阶段失败。当前的 49/68 分支覆盖高于门槛，但仍有 19 个分支未覆盖。评审者须查看报告和断言；构建通过也可能算错工资。本地 clean 验证通过了检查；每个拉取请求仍须提供自己的 CI 结果。
