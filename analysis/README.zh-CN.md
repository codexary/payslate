# 预扣税表检查

`tax_schedule_check.py` 用 Z3 证明：薪资引擎读到的那份预扣税表，对每一个整分毛工资（$0.00 到 $1,000,000.00）都成立。它从引擎用的同一份 CSV 读税表——并检查 pay-engine 与 legacy-engine 两份完全一致——再用精确有理数按整分建模引擎的计算（`base + (毛工资 - 起点) * 费率`，HALF_UP 舍入到分），对文件里的每一档证明：预扣非负、不超过毛工资、在各档交界处连续、毛工资每涨一分预扣最多跳一分（因此净工资不下降；这要求每档费率在 0 到 1 之间，脚本会检查）。它从每个带费率档的费率推出跳点周期并验证，并报出各档第一次跳动的最小毛工资。

因为它读 CSV，税表一改就真的被重新检查。运行最后演示：把一份故意不连续的税表（最高档的 base 抬高 $10.00）喂进去，连续性证明会失败——所以这项检查不会把一份坏税表悄悄放过。

## 运行

Python 3.12，固定版本求解器，从仓库根目录运行（CSV 路径相对根目录）：

    python3 -m venv .venv && . .venv/bin/activate
    pip install -r analysis/requirements.txt
    python3 analysis/tax_schedule_check.py

每条性质打印 `proved`，反例打印 `FAILED (as expected)`；输出是确定的。想手工复现失败，把 example-tax-schedule.csv 里某档的 base 改得让某个交界不再对齐，再运行脚本：那个交界的连续性性质会打印 `FAILED`。
