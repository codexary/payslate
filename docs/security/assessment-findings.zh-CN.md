[English](assessment-findings.md) | 简体中文

# 交给外部评估的安全发现清单

这份清单是团队为 2026-08-27 开始的外部安全评估整理的。每一项都是评估之前就在本仓库里发现并关闭的缺陷，连同修复它的提交和如今守护它的回归。这是团队自己对已知问题及其关闭情况的记录，不是评估方的报告；结论由评估方自己下。仓库里的数据全是合成的。

提交标识是本仓库历史里的短 SHA。威胁模型行见 [docs/security/threat-model.zh-CN.md](threat-model.zh-CN.md)。

| # | 发现 | 严重度 | 威胁模型行 | 修复提交 | 守护的回归 |
| --- | --- | --- | --- | --- | --- |
| 1 | 员工搜索把文本拼进 SQL，构造的文本能匹配或带出其他行 | 高 | 搜索文本到 SQL | `831917e` | `IntegrationEmployeeSearchSecurityTest`（`5490183` 引入） |
| 2 | 员工搜索没有按调用者所属雇主限定，能读到另一个雇主的行 | 高 | API 到 PostgreSQL | `0a0c64f` | `IntegrationEmployeeSearchSecurityTest`（`5490183`） |
| 3 | 错误响应把内部细节（栈与异常文本）带给了调用者 | 中 | 浏览器到 API | `92c1812` | `SystemErrorBoundaryTest` |
| 4 | 工时表上传整份读入，没有大小、行数、编码上限，有资源耗尽风险 | 中 | CSV 上传到 API | `defbf2f` | `SystemTimesheetUploadRobustnessTest` |
| 5 | 同一员工重叠或重复的班次被照收，同一份工作付两次 | 中 | 同一员工的 CSV 上传 | `c069019` | `SystemTimesheetOverlapTest` |
| 6 | 转换器把账户名拷进定长输出字段时不设边界，溢出了栈缓冲区 | 高 | 旧导出到 aba-convert | `bdd95d6` | `legacy/aba-convert/tests/run-sanitizers.sh` 重放 `fuzz/crashes/name-overflow.txt`（`da82df6`） |
| 7 | 转换器把批次合计累加在可能溢出的类型里，写出的合计被破坏 | 高 | 旧导出到 aba-convert | `4b12656` | `legacy/aba-convert/tests/run-sanitizers.sh` 重放 `fuzz/crashes/total-overflow.txt`（`10232cb`） |
| 8 | ABA 读取器保留了继承自桌面端的冲正引用分支，会收下格式错误的引用 | 低 | 本地 ABA 到银行交接 | `2bcdc80` | `bank-file` 的 `AbaReferenceFuzzTest` 种子 `crash-reversal-reference` |
| 9 | 被静默截断的桌面导出被当作完整的照常转换 | 中 | 本地 ABA 到银行交接 | `5f20f99` | `legacy/aba-convert/tests/control-trailer.sh`、`run-reconcile-cases.sh` |
| 10 | 明细金额字段带尾部空格时被收下，而不是被拒 | 低 | 本地 ABA 到银行交接 | `0b4370a` | `legacy/aba-convert/tests/amount-trailing-space.sh` |

## 说明

- 严重度是对本系统上影响和可达性的定性判断，不是打分向量。两处转换器内存安全修复（第 6、7 项）另以安全公告 [PAYSLATE-2026-0001](advisories/PAYSLATE-2026-0001.zh-CN.md) 发布，带 CVSS 3.1 向量与分数——转换器是本地工具，有些站点仍在旧版本上运行它。
- 第 6、7、9、10 项在继承来的桌面转换器 `aba-convert` 里，托管服务并不运行它；第 1–5 项在托管 API 里。
- 每个修复都和守护它的回归落在同一分支上；差分与解析器模糊器、sanitizer 重放和对账用例把它们钉在关闭状态。有回归的修复是防复发的关闭，不等于保证没有相关问题残留；评估要找的正是这份清单没覆盖的地方。
