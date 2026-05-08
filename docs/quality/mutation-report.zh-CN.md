[English](mutation-report.md) | 简体中文

# 变异测试报告

Olivia Brooks — 2026-05-08

## 范围与结果

完整 `pay-engine` 分析使用 PIT 1.30.1、`pitest-junit5-plugin` 1.2.3 和默认变异算子。当前代码用 `HALF_EVEN` 舍入 YTD 累计金额。`LegacyPayEngine.calculate` 在排序前逐一检查班次是否为空、起止时间是否缺失，以及结束时间是否晚于开始时间；时长上下限仍在排序后的计算循环中检查。

| 指标 | 结果 |
| --- | ---: |
| 变异范围内的行覆盖 | 81/98（显示为 83%） |
| 生成的变异体 | 61 |
| 杀死 | 49 |
| 存活 | 8 |
| 未覆盖 | 4 |
| 原始变异分数 | 49/61 = 80.33%（显示为 80%） |
| 已覆盖变异体上的 test strength | 49/57 = 85.96%（显示为 86%） |
| PIT 检查的测试数／测试执行次数 | 11 / 362 |
| 已证明等价的存活项 | 2 |
| 仅扣除已证明等价项后的调整分数 | 49/59 = 83.05% |

行覆盖的分母来自 PIT 的变异类范围，与 JaCoCo 的 bundle 分母不同。Test strength 不计四个未覆盖项。两个已证明等价项在 PIT 中仍为 `SURVIVED`，只在单独列出的调整分数中扣除。六个未分类存活项和四个未覆盖项均保留在原始分数与调整分数的分母中。表中的测试计数描述本次分析，并非完整 JUnit 测试套件的规模。

## 存活变异体

八个存活项均位于 `pay-engine/src/main/java/com/elsworne/payslate/engine/LegacyPayEngine.java`。行号对应当前源码。下表算子名称为省略包名前缀的 PIT 类名。

| 方法 | 行号 | 算子 | 改动 | 审查分类 |
| --- | ---: | --- | --- | --- |
| `calculate` | 17 | `ConditionalsBoundaryMutator` | `hourlyRate.signum() < 0` → `<= 0` | 未分类 |
| `calculate` | 24 | `VoidMethodCallMutator` | 删除 `Collections.sort` | 未分类 |
| 匿名比较器 `compare` | 25 | `PrimitiveReturnsMutator` | 返回 `0`，不再比较开始时间 | 未分类 |
| `calculate` | 36 | `ConditionalsBoundaryMutator` | `minutes > 24*60` → `>= 24*60` | 未分类 |
| `calculate` | 36 | `ConditionalsBoundaryMutator` | `minutes < 1` → `<= 1` | 未分类 |
| `calculate` | 38 | `ConditionalsBoundaryMutator` | `paidMinutes < 180` → `<= 180` | 未分类 |
| `overtimeFactor` | 85 | `ConditionalsBoundaryMutator` | `factor < tier` → `<= tier` | 在所述输入域内已证明等价 |
| `withholding` | 97 | `ConditionalsBoundaryMutator` | `gross.compareTo(candidate) >= 0` → `> 0` | 使用随代码提供的示例税表时已证明等价 |

[等价变异体说明](../test-design/equivalent-mutants.zh-CN.md) 给出两个证明及其适用范围。存活本身既不能证明等价，也不能证明存在生产缺陷。其余六项须结合输入约定和预期结果审查，包括调整班次输入顺序是否会改变工资，以及边界输入能否产生可观察的差异。

四个 `NO_COVERAGE` 项均为 `PayCli.main` 中的条件取反，位于 `PayCli.java` 第 17、23（两个变异体）、25 行。这四项均未判定为等价。

## 舍入断言

对输入 `0.005`，`roundedTotalIsNotNegative` 既接受 half-up 舍入得到的 `0.01`，也接受规则要求的 half-even 结果 `0.00`。它对分值的非负断言同样接受返回零。`roundsHalfCentToEvenAndReportsExactCents` 要求先得到 `0.00`，再加一笔后得到 `12.34`，并且恰好返回 `1234` 分。该测试在改为 `HALF_EVEN` 之前暴露了舍入错误，现在杀死了 `YtdAccumulator.roundedCents` 第 12 行的返回零变异体。`YtdAccumulator` 生成的两个变异体均被杀死。

## CI 配置与审查

`.github/workflows/ci.yml` 中的 `mutation-changed` job 在 pull request 上运行。它比较拉取请求基线与 `HEAD` 之间 `pay-engine/src/main/java` 下的 Java 源文件路径，把每条改动路径转换为类名，再分别用 `-DtargetClasses=<class-name>*` 调用 PIT。通配符包含嵌套类。没有匹配的 Java 路径时，循环不会调用 PIT，因此只修改测试不会触发这个 job 中的变异分析。

`.github/workflows/mutation-nightly.yml` 的完整模块任务计划每天 03:15 UTC 执行（`15 3 * * *`），也支持手动触发。命令为：

```sh
./mvnw -B -ntp -pl pay-engine test-compile org.pitest:pitest-maven:mutationCoverage
```

按改动类分析能提供有针对性的反馈；夜间配置还覆盖拉取请求未改动的生产类。两种范围都不能证明所有交互正确。当前不设数值型变异分数门槛。评审者须检查存活项和未覆盖项，并为调整分数中的每一项扣除提供理由。
