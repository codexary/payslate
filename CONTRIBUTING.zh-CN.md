[English](CONTRIBUTING.md) | 简体中文

# 参与开发

- 使用 Java 21。提交评审前先运行 `./mvnw -B -ntp verify`。构建会用 Spotless（google-java-format）检查 Java 代码格式，`./mvnw spotless:apply` 可以自动改好。`LegacyPayEngine` 在重构之前不参与格式检查。
- 在名为 `<类型>/<主题>` 的分支上工作，例如 `fix/midnight-day-rate`，按模板开拉取请求。提交信息遵循 Conventional Commits。
- 修缺陷先写一个在现有代码上失败的测试，测试和修复放在同一个拉取请求里。
- 预期工资以产品负责人确认过的书面规则为准，不以引擎当前算出的结果为准。
- 只使用合成数据。不要提交真实员工的姓名、税号或银行信息。
