[English](README.md) | [简体中文](README.zh-CN.md)

# Payslate

Payslate 原为中小企业提供薪资软件。Payslate Online 是基于 2004 年遗留薪资引擎建设的托管服务。目前的模块是 `pay-engine` 和 `bank-file`；后续将增加用于导入工时表、运行薪资批次和下载 ABA 文件的 Web API 与数据库。本仓库仅使用合成数据。测试 BSB 只检查字段格式，生成的文件不能提交给银行。

## 构建和运行

需要 Java 21。运行 `./mvnw -B -ntp verify`。`docker` profile 留给容器集成测试。演示 CLI：

```sh
./mvnw -q -pl pay-engine package
java -cp pay-engine/target/classes com.elsworne.payslate.engine.PayCli pay-engine/demo-timesheet.csv 20 PERMANENT
```

预期输出：

```text
Gross: 20.00
Tax: 0.00
Net: 20.00
```

`pay-engine/src/main/resources/example-tax-schedule.csv` 是**测试用示例**，并非官方税表。生产环境须每财年加载官方表。CLI 的 CSV 格式刻意保持简单；排班软件格式的 spike 将研究产品导入器。

## 关于本仓库

Elsworne、Payslate 和团队成员均为虚构。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
