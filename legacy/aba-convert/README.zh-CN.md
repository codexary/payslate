# ABA 批次转换器

一个从 Payslate 桌面产品继承来的小命令行工具。它读取定宽的批次文件，把银行的 ABA 版式写到标准输出。

```sh
make
./aba-convert tests/normal.txt
```

输入是一行以 `H,PAYSLATE` 开头的表头，随后每笔付款一行明细：一个 `D`、40 个字符的账户名、7 个字符的 BSB、
9 个字符的账号，以及 10 位的以分为单位的金额。表头错误或明细行过短时以状态 2 退出。

有两处继承下来的弱点原样保留，供健壮性审查复现：账户名被拷入一个 24 字节的缓冲区而没有长度检查，批次合计用
32 位有符号整数累加。构建 sanitizer 变体并重放样例输入即可看到：

```sh
sh tests/run-sanitizers.sh
```

`scripts/build-container.sh` 和 `scripts/build-sanitizers-container.sh` 在钉死版本的 `gcc:14.2.0` 容器里构建这
两个变体。
