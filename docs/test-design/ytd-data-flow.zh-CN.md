[English](ytd-data-flow.md) | 简体中文

# 年初至今累计值的定义—使用分析

Olivia Brooks — 2026-04-23

## 原样移植的方法

2026-04-20，`YtdAccumulator` 从 Payslate 7 桌面产品原样移植到 `pay-engine`。负调整时清零是继承下来的缺陷。修复前，方法在两项参数空值检查之后执行以下计算：

```java
BigDecimal total = yearToDate;                      // d1，读取 y0/y1
if (adjustment.signum() < 0) total = BigDecimal.ZERO; // d2，条件读取 a0
total = total.add(gross).add(adjustment);            // d3，读取 d1/d2、g0、a0
yearToDate = total;                                 // y1，读取 d3
return total;                                       // 读取 d3
```

`yearToDate` 初始为 `BigDecimal.ZERO`（`y0`），每次成功调用在 `y1` 处重新定义它。参数 `gross`（`g0`）和 `adjustment`（`a0`）在方法入口处定义，计算前的 `Objects.requireNonNull` 分别读取这两个参数。`total` 有三个定义：复制此前 YTD 的 `d1`、负调整时错误清零的 `d2`，以及计算新累计值的 `d3`。只有中途没有同一变量的新定义覆盖旧值，某次定义才能到达后续使用。

## 修复前可行的定义—使用配对

两项测试都在 `YtdAccumulatorTest` 中：

- **原测试：**`accumulatesOrdinaryAndPositiveAdjustment` 先调用 `add(100.00, 0)`，再调用 `add(50.00, 10.00)`，分别断言结果为 `100.00` 和 `160.00`。
- **回归测试：**2026-04-21 加入的 `negativeAdjustmentRetainsEarlierPay` 先调用 `add(100.00, 0)`，再调用 `add(50.00, -10.00)`，断言第二次结果为 `140.00`。

每项测试都创建一个新的累计器。下表列出两项测试在原样移植的方法中执行的路径，也包括回归测试在断言失败前已经执行的部分。

| 变量 | 可行的定义—使用配对 | 原测试 | 回归测试 |
| --- | --- | --- | --- |
| `yearToDate` | 首次调用的 `y0 → d1` | 已覆盖 | 已覆盖 |
| `yearToDate` | 下次调用的 `y1 → d1` | 已覆盖 | 已覆盖 |
| `total` | 调整非负时的 `d1 → d3` | 已覆盖 | 首次调用已覆盖 |
| `total` | 调整为负时的 `d2 → d3` | 未覆盖 | 第二次调用已覆盖 |
| `total` | `d3 → y1` | 已覆盖 | 已覆盖 |
| `total` | `d3 → return` | 已覆盖 | 已覆盖 |
| `gross` | `g0 → null check` | 已覆盖 | 已覆盖 |
| `gross` | `g0 → d3` | 已覆盖 | 已覆盖 |
| `adjustment` | `a0 → null check` | 已覆盖 | 已覆盖 |
| `adjustment` | `a0 → signum` | 已覆盖 | 已覆盖 |
| `adjustment` | `a0 → d3` | 已覆盖 | 已覆盖 |

以 `y0、y1、d1、d2、d3、g0、a0` 为定义集合，原测试让七个定义中的六个沿着未被重新定义的路径到达使用处，故 **all-defs 为 6/7**。它覆盖表中 **10/11** 个可行配对，仅缺 `d2 → d3`，因此**不满足 all-uses**。在这个模型下，单独运行回归测试就能达到 **all-defs 7/7**、**all-uses 11/11**：首次调用覆盖 `d1 → d3`，第二次覆盖 `d2 → d3`。覆盖配对并不意味着断言通过。

计数包含方法入口和字段的定义，也包含空值检查中的读取。`signum` 按一个使用位置计数，不把真假出口分别计算。两项测试都传入非空参数，均未断言空值检查抛异常的行为。

## 回归测试、修复与重构

修复前，回归测试得到的是 **40.00，而非 140.00**。第二次调用时，`d1` 先复制此前的 `100.00`，但在 `d3` 读取 `total` 之前，`d2` 已将它覆盖为零。两条路径都会到达同一条加法语句，负调整路径却丢失了此前的工资。

2026-04-21 的修复将负调整分支改为 `total = yearToDate`，保留此前的累计值，两项测试随后均通过。这次赋值重复了 `d1` 已复制的值，并多读了一次 `yearToDate`；上表描述的是修复前的方法，不适用于这个中间版本。

2026-04-22 的重构删除了多余分支和局部变量 `total`。当前方法保留两项空值检查，只用一条赋值语句写回字段：

```java
yearToDate = yearToDate.add(gross).add(adjustment);
return yearToDate;
```

两项测试仍然通过。每次成功调用先读此前的 YTD，加上工资和调整，再写回字段一次。旧的 `total` 定义 `d1`、`d2`、`d3` 已不存在，不能继续用原来的 11 个配对作为当前实现的覆盖率分母。测试验证了这两个连续调用的例子，并未验证所有算术、币种、舍入或跨年规则。
