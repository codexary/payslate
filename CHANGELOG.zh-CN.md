[English](CHANGELOG.md) | 简体中文

# 变更日志

Payslate 的所有重要变更都记在这里。格式遵循
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号采用
[语义化版本](https://semver.org/lang/zh-CN/spec/v2.0.0.html)。

## [1.0.0] - 2026-08-28

首个发布版本。本仓库的数据全是合成的。

### 新增

- 托管薪资 API（Spring）：OIDC bearer 认证、办事员与员工的角色校验、按雇主限定的数据访问，以及指标面板上被拒请求的安全事件。
- 薪资引擎：重构后的 `PayEngine` 从精确整数分钟算出工资、税、养老金与年假，用特征化、属性与差分测试和冻结的 2004 引擎对照；预扣税表用 Z3 证明。
- 工时表 CSV 上传：严格 UTF-8、大小与行数上限、带行号的错误，并拒绝重叠或重复的班次。
- ABA 银行文件导出，以及继承来的本地 `aba-convert` 转换器（要求可对账的控制尾行）。
- 外部评估前的健壮性测试：对解析器与两套薪资引擎用 Jazzer，对转换器用本地 AFL++ 与混合（SymCC）模糊测试，对批次读取器用 KLEE，并在 CI 里做定时模糊测试。

### 修复

- `aba-convert` 现在会拒绝控制尾行缺失或对不上账的桌面导出，不再把一份被静默截断的文件当作完整文件照常转换。
- `aba-convert` 现在会拒绝不是正好十位数字的明细金额字段，例如带尾部空格的金额。

### 安全

- `docs/security/assessment-findings.zh-CN.md` 列出评估前关闭的每一项安全发现，连同修复提交与守护的回归。
- 安全公告 [PAYSLATE-2026-0001](docs/security/advisories/PAYSLATE-2026-0001.zh-CN.md) 覆盖两处 `aba-convert` 内存安全修复（账户名栈缓冲区溢出，CVSS 7.8；批次合计整数溢出，CVSS 5.5）。
