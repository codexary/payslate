[English](0014-scheduled-fuzzing.md) | 简体中文

# ADR 0014：定期运行模糊测试

Ravi Menon — 2026-08-05

## 状态

2026-08-05 采纳。

## 背景

在外部安全评估前，团队增加了覆盖率引导的健壮性测试：使用 Jazzer 测试 ABA 解析器和工时表导入器，使用 AFL++ 测试 C 语言转换器 `aba-convert`。仓库使用 GitHub 免费计划，每月的 Actions 运行分钟数有额度限制，因此定期运行的测试必须限定时长。

## 决定

每次提交拉取请求时，在 [ci.yml](../../.github/workflows/ci.yml) 的 `fuzz-smoke` 作业中，对 `AbaReferenceFuzzTest` 和 `TimesheetImporterFuzzTest` 各运行一次短时 Jazzer 模糊测试。Jazzer 每次启动 JVM 只对一个 `@FuzzTest` 进行模糊测试，其余会被跳过，因此必须分别调用，每次用独立的 `-Dtest` 指定一个目标。每个目标设置 `-Djazzer.max_duration=60s`，使两者合计的实际运行时间保持在两分钟左右。Jazzer 0.30.0 会忽略 `-Djazzer.max_total_time`，不得用该属性限制运行时长。

通过 [fuzz-nightly.yml](../../.github/workflows/fuzz-nightly.yml) 每晚对这两个目标运行更长时间的测试，仍然分别调用，每个目标设置 `-Djazzer.max_duration=5m`，将两者合计的运行时间控制在十分钟以内。如果过去一天没有新提交，则跳过定时模糊测试。支持通过 `workflow_dispatch` 手动启动，即使没有近期提交也照常运行；使用 `concurrency` 并设置 `cancel-in-progress: true`，取消同一 ref 下尚未结束的运行。

不将针对 `aba-convert` 的 AFL++ 测试纳入 CI。它需要固定版本的 sanitizer 镜像 `aflplusplus/aflplusplus:v5.02c`，每秒只能执行几百次测试，每次拉取请求或每晚安排一次短时间运行，收益有限。转换器有改动时，在本地运行 AFL++；随后逐一分析崩溃原因、精简触发输入，并将其作为 sanitizer 回归用例提交到 [fuzz/crashes/](../../legacy/aba-convert/fuzz/crashes/)，由 [tests/run-sanitizers.sh](../../legacy/aba-convert/tests/run-sanitizers.sh) 重放。

重叠班次检测的性质测试在 `verify` 中作为确定性回归测试运行，不列入定期模糊测试的目标。

## 考虑过的其他方案

- 用 `-Djazzer.max_total_time` 限制时长：无效。Jazzer 0.30.0 会忽略该属性，实际仍按测试入口注解中指定的时长运行。
- 在一次调用中列出全部目标：不采用。Jazzer 每次启动 JVM 只对一个 `@FuzzTest` 进行模糊测试，其余全部跳过。
- 将 AFL++ 加入每晚的工作流：不采用。它需要 sanitizer Docker 镜像，执行速率又低，每次定时运行的收益有限。
- 每次拉取请求都运行一场长时间模糊测试：不采用。大多数拉取请求并不修改解析器，却都要承担额外的等待时间。

## 后果

短时模糊测试没有发现问题，只能说明在这段时间内未触发问题，不能据此认定解析器安全。每个保存下来的崩溃都必须先复现、精简触发输入，并转为确定性回归测试，再修复缺陷。

修改工作流时，必须保留对各目标的独立调用：Jazzer 每次调用只对一个目标进行模糊测试，合并调用会导致其他目标被跳过，容易让人误以为它们也已接受测试。
