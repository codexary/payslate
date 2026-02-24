[English](weekend-penalty.md) | 简体中文

# 跨入周六的咖啡店班次

Olivia Brooks — 2026-02-24

`LegacyPayEngineTest.cafeFridayShiftCrossingMidnightPaysSaturdayPenalty` 沿用客户报告中那一班的时间：2026-02-13 周五 23:00 至 2026-02-14 周六 01:00。员工换成合成数据：固定雇员，时薪 $20，此前没有工时，班次也不是公共假日。

独立核算预期总工资：周五一小时 $20，加周六一小时 $20 × 1.25，共 **$45.00**。遗留引擎返回 **$40.00**，少付 **$5.00**。

| 术语 | 观察到的因果链 |
| --- | --- |
| 缺陷（fault） | `day = s.start().getDayOfWeek()` 每班次只计算一次。 |
| 错误状态（error state） | 周六 00:00 时，`clock.toLocalDate()` 已前进到新的一天，循环却仍用 `FRIDAY` 表示当前分钟的星期几。 |
| 失效（failure） | 总工资为 $40.00，而非 $45.00。 |

`saturdayAndSundayPenalties` 中的班次都从所检查费率对应的日期开始，无法区分“每班次选一次费率”和“每分钟选一次费率”。本回归测试保留完整的跨午夜班次，并断言独立计算的总额。要求是每个付费分钟按当时的星期几选日期费率；修复引擎后仍须保留这条失败用例。
