# ABA 批次转换器

一个从 Payslate 桌面产品继承来的小命令行工具。它读取定宽的批次文件，把银行的 ABA 版式写到标准输出。

```sh
make
./aba-convert tests/normal.txt
```

输入是一行以 `H,PAYSLATE` 开头的表头，随后每笔付款一行明细：一个 `D`、40 个字符的账户名、7 个字符的 BSB、
9 个字符的账号，以及 10 位补零的以分为单位的金额（只能是数字，字段中任何位置出现空格都会被拒绝）。批次以一行控制尾记录结束：一个 `T`、6 位记录条数、10 位以分为单位的合计，之后除了行尾不能有别的内容。转换器先读完整份输入，只有在表头、记录条数和合计都对得上时才写输出；表头错误、明细行过短、尾记录缺失或对不上、尾记录带多余字符、空批次、尾记录之后还有数据，都以状态 2 退出。没有尾记录的导出（较旧的桌面版本）会被拒绝——请从打过补丁的 Payslate 7 重新导出。

有两处继承下来的弱点经健壮性审查发现并已修复：账户名曾被拷入一个 24 字节的缓冲区而没有长度检查，批次合计曾用
32 位有符号整数累加。现在拷贝被限制在 24 个字符的输出字段内，合计改用 64 位整数累加并做范围校验。最小化后的崩溃
作为 sanitizer 回归用例保留，必须保持干净：

```sh
sh tests/run-sanitizers.sh
```

用 AFL++ 做的覆盖率引导健壮性测试（模糊测试）驱动 ASan/UBSan 构建。在终端里跑一次本地测试即可看到状态屏，再对
保存下来的崩溃分诊；新的崩溃用 `afl-tmin` 最小化后，作为 sanitizer 回归用例放到 `fuzz/crashes/` 下：

```sh
sh fuzz/run-afl.sh 120
sh fuzz/triage.sh
```

`scripts/build-container.sh` 和 `scripts/build-sanitizers-container.sh` 在钉死版本的 `gcc:14.2.0` 容器里构建这
两个变体。

`klee/` 下是批次读取函数（`convert.c` 里的 `read_batch`）的 KLEE harness。它在官方镜像 `klee/klee:v3.2` 里运行，该镜像只有 amd64 版本，在 Apple 芯片上通过模拟运行：

```sh
sh klee/run-klee.sh 1   # 探索一条记录的批次，测试写到 klee/out
sh klee/run-range.sh    # 单笔金额的范围检查
sh klee/replay.sh       # 在本地用 ASan 回放生成的测试，并统计 gcov 覆盖
```

`symcc/` 下是混合模糊测试实验用的钉死版本镜像：带 QSYM 后端的 SymCC 和 AFL++ `v5.02c`，两者都用 LLVM 15 构建，所以转换器和模糊器由同一个编译器插桩。它不像 KLEE 镜像那样靠模拟，而是在 arm64 上原生构建、原生运行。先构建一次：

```sh
sh symcc/build-image.sh   # 镜像标签 payslate-aba-symcc
```

从一个种子出发跑 SymCC，看它能解出什么：

```sh
sh symcc/run-symcc.sh                             # 从一个金额被改过的批次出发（默认）
sh symcc/run-symcc.sh fuzz/seeds/two-records.txt  # 从一个已经对得上账的批次出发
```

在安静的时间窗口里，把混合模糊测试（AFL++ 旁边加 SymCC）和单独 AFL++ 放到不相交的 CPU 核上重复多次比较：

```sh
sh symcc/compare.sh 5 600   # 5 次，每次 10 分钟；结束时打印汇总
```

`compare.sh` 每次运行 `run-hybrid.sh` 和 `run-afl-pair.sh`，再调用 `summarise.py`，后者读取每次运行的 `plot_data`，并回放队列，统计那些金额不同于种子却仍然对得上账的批次。
