[English](0006-preserve-ytd-on-adjustment.md) | 简体中文

# ADR 0006：负调整保留此前的 YTD

Ravi Menon — 2026-04-24

## 状态

2026-04-24 采纳。

## 背景

2026-04-20，Payslate Online 将 `YtdAccumulator` 从 Payslate 7 桌面产品原样移植到 `pay-engine`。继承来的方法先把 `yearToDate` 复制到局部变量 `total`；遇到负 `adjustment`，又把 `total` 设成 `BigDecimal.ZERO`。下一行从这个零开始加入 `gross` 与 `adjustment`，再把结果写回 `yearToDate`。清零缺陷来自桌面产品。

原有测试 `accumulatesOrdinaryAndPositiveAdjustment` 只覆盖零和正调整。Olivia 的数据流笔记 [docs/test-design/ytd-data-flow.md](../test-design/ytd-data-flow.md) 记录了负调整路径上未测试的 `d2 → d3` 配对。回归测试 `negativeAdjustmentRetainsEarlierPay` 先加入 `100.00`，再加入 `50.00` 和 `-10.00`，期望 `140.00`。2026-04-21 修复前，继承的实现返回 `40.00`。修复后以及 2026-04-22 重构后，两项测试均通过。

## 决定

负调整只改变本次加入的金额；先前的 YTD 不清零。每次 `add` 先读旧 YTD，再把工资与调整相加，只在一个位置写回字段。保留普通及正调整测试，也保留负调整测试。

保留当前实现的空值检查，以及唯一的计算赋值语句 `yearToDate = yearToDate.add(gross).add(adjustment)`，随后执行 `return yearToDate`。

## 考虑过的方案

- 保留清零，把它解释成跨年操作。没有采用：方法没有年份边界输入，回归测试显示普通修正会丢失此前金额。
- 把零改成 `yearToDate`，继续保留条件分支。这是第一步修复，但它重复赋入 `total` 已有的值。随后重构删除了多余分支。

## 后果

- 修正测试固定了连续两次调用、旧 YTD 非零时的行为。
- 当前方法不再有负调整分支和局部 `total`；它的数据流目标不同于继承来的方法。笔记中修复前的 11 个配对不能衡量当前方法的覆盖情况。
- 本决定只规定当前累计器的算术含义。跨年、币种和金额舍入还需要各自的规则与测试。
