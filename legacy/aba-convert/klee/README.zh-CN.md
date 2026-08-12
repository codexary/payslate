[English](README.md) | 简体中文

# 对批次读取函数做符号执行

`harness.c` 把一份小批次里金额和控制尾记录的低位数字设为符号输入，再调用 `read_batch`（结构是具体值，路径数因此有界）。`klee_assert` 检查被接受的批次能对上账：它的记录条数和合计等于控制尾记录声明的值。`range_check.c` 是一个单独的 harness，让十个符号数字走一遍单笔金额的范围检查，说明其中的溢出分支走不到。

全部在官方 KLEE 镜像（`klee/klee:v3.2`）里运行。这个镜像只有 amd64 版本，在 Apple 芯片的机器上通过模拟运行。构建命令写在脚本里；不报告任何时间。

## 脚本

- `run-klee.sh [N]`：对一份 `N` 条记录的批次运行 KLEE（默认 2），测试写到 `klee/out`。
- `run-range.sh`：运行范围检查的 harness，结果写到 `klee/out-range`。
- `replay.sh [klee/out]`：用 KLEE 的回放库（`libkleeRuntest` 加 `KTEST_FILE`），把每个生成的 `.ktest` 交给同一个 harness 和 `read_batch` 的本地构建回放。回放时 `klee_make_symbolic` 读回求解出的字节，harness 逐字节重建 KLEE 当时探索的那份批次。构建用 gcc，因为 KLEE 镜像里的 clang 没有 AddressSanitizer 运行库；gcc 同时提供 ASan 和 `--coverage`。运行会报告检查器的任何发现，并借助 gcov 列出没有任何输入走到的行（其中包括 “amount out of range” 分支）。

## 重新生成对账回归用例

`tests/reconcile-cases/` 为每一种退出结果保留一份能读的批次，从 KLEE 生成的完整集合中挑出。要重新生成完整集合（每个 `.ktest` 一份批次，按字节去重）并查看：

```sh
sh klee/run-klee.sh 1                 # 一条记录时生成 57 个测试，放在 klee/out
rt=/tmp/klee_build160stp_z3/lib       # 在 KLEE 镜像里
gcc -DDUMP_BATCH -I/tmp/klee_src/include -I. klee/harness.c -o /tmp/dump \
    -L"$rt" -lkleeRuntest -Wl,-rpath,"$rt"
for t in klee/out/*.ktest; do KTEST_FILE="$t" BATCH_OUT="/tmp/$(basename "$t" .ktest).bin" /tmp/dump; done
```

`DUMP_BATCH` 让 harness 把重建的批次写到 `$BATCH_OUT`，而不是去解析它。提交的用例是从这个集合里挑出的各种结局：每种拒绝消息一份，外加一份被接受的批次；优先选全部可打印的输入，被接受的那份用规范的十位金额。`tests/run-reconcile-cases.sh` 断言每一份的退出码和消息完全符合预期，并检查对账不变式。
