[English](0002-casual-loading-order.md) | 简体中文

# ADR 0002：日期费率确定后加临时雇员 loading

## 状态

2026-03-11 采纳。

## 背景

临时雇员普通工时加 25% loading，周末和公共假日也适用。原来的说法没有交代：先算日期费率再乘 1.25，还是另加基本时薪的 0.25。两种算法会给出不同工资。Margaret O'Connell 在 `docs/specs/casual-employees.md` 确认了规则。

## 决定

每个普通分钟先确定日期费率，临时雇员再加基本时薪的 0.25。公共假日普通工时因此是基本时薪的 2.25 + 0.25 = 2.50 倍。加班取加班档位和日期费率中较高者，不加 loading。保留 `casual_public_holiday_interaction` 和 `casual_overtime_no_loading` 作为有名称的回归分区。

## 考虑过的方案

- 将日期费率乘 1.25。公共假日普通工时会成为基本时薪的 2.8125 倍，违背 Margaret 确认的规则。
- 加班也加 loading。这同样违背书面规则。

## 后果

日期类别与雇佣类型必须组合测试。预期结果来自书面规则，不能仅由当前引擎输出决定。其他 award 若有不同的 loading 顺序，必须另写清楚。
