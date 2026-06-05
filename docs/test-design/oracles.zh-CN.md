[English](oracles.md) | 简体中文

# 工资计算的测试判据

Olivia Brooks — 2026-06-05

`legacy-engine` 是冻结的 Payslate 7 计算器，源码只修改了 package 声明。`PayEnginePropertiesTest.parallelRunOracle` 生成时薪、班次时长、雇佣类型、节假日标志和日期，在 100 次运行中比较总工资、税额、退休金和年假。新实现用 `BigDecimal` 计算金额，用 `java.time` 处理日期。与旧系统一致可以作为迁移判据，但不能证明继承的 award 规则本身正确。

比较按 `BigDecimal` 的数值判断相等。输入均为单个班次：2026 年 2 月 22 日至 28 日的 08:00 开始，时长为 1 至 600 分钟，整数时薪为 1 至 500，雇佣类型为正式员工或临时员工，节假日标志可为真或假。旧接口没有扣款参数，所以新接口也传入零扣款。生成器不覆盖多个班次或跨午夜的情况；另有 `refactoredEngineResetsWeeklyOvertimeAcrossSundayMidnight` 检查在前一周已工作 38 小时后，从周日跨到周一的班次，时薪为 $20，期望总工资为 $785.00、加班时数为 0.5000。

净工资性质发现了一个实际的扣款符号缺陷。jqwik 的原样输出为：

```text
Shrunk Sample (1 steps)
-----------------------
  arg0: 1

Original Sample
---------------
  arg0: 64
```

扣款一元、总工资 $20 时，正确净工资为 $19.00，错误实现为 $21.00。修复将加法改成减法：`gross.subtract(tax).subtract(deductions)`。随机运行时，原始样本可能不同。`netEqualsGrossMinusTaxMinusDeductions` 最初生成 0 至 100 的扣款；加入可用工资上限后，范围改为 0 至 20。班次固定为正式员工在非节假日的周一工作一小时，时薪 $20。示例税表对这笔总工资计算出的税额为零，因此所有生成的扣款都在可用工资范围内。

其他性质检查总工资单调不减、税额非负，以及 ABA 付款往返一致：

- `moreHoursNeverLowerGross` 比较一个 1 至 600 分钟的班次与延长一分钟后的同一班次，员工为正式员工，时薪 $20，日期为非节假日的周一。
- `taxIsNeverNegative` 为正式员工在非节假日周一的一小时班次生成 1 至 1,000 的整数时薪。所用税表是合成示例，并非官方税表。
- `AbaPropertiesTest.abaRoundTripPreservesPayments` 为只有一笔付款的批次生成 1 至 9,999,999 分的金额，运行 100 次，其他字段固定。它断言 `parse(write(batch)).payments()` 恰好包含原来的付款。这检查的是付款是否保留，不能证明所有批次字段都正确或文件能被银行接受；写入器和解析器共有的错误也可能逃过检查。

Margaret 的规则是：扣款超过总工资减税时，拒绝整笔工资批次，并在错误中指出员工。净工资可以为零，但不得为负。当前 `PayEngine.calculate` 在单名员工的计算中执行这一上限：`deductionsAtAvailablePayYieldZeroNet` 允许从 $20.00 可用工资中扣除 $20.00；`deductionsOverAvailablePayRejectNamedEmployee` 则拒绝 $20.01 的扣款，错误信息为 `Deductions exceed available pay for employee Alice Test`。不接收员工姓名的重载使用 `unknown employee`。这些测试尚不能证明整批拒绝或回滚；计算接入工资批次后，仍须验证这一要求。
