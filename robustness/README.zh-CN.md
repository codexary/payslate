[English](README.md) | 简体中文

# 固定种子的 HTTP 健壮性测试

这套工具向团队自己的本地 Payslate API 发送合成输入，通过 HTTP 响应观察问题，不修改产品代码。它需要手动运行，不属于 CI。请使用可随时删除的本地环境：成功上传会保存班次，事件请求也可能改变工资批次的状态。

测试需要完整的 Compose 服务栈，因为 multipart 请求处理、身份认证、CSV 解析、员工查询、数据库写入和错误映射都会影响最终响应。只测试解析器无法观察这些环节之间的相互作用。运行时可以结合指标和 Grafana 仪表盘分析耗时。

## 检查内容与输入生成

一个响应可能同时触发多项检查：

| 报告名称 | 判定条件 |
| --- | --- |
| `server-error` | HTTP 状态码至少为 500。 |
| `problem-format` | 4xx/5xx 响应不是 `application/problem+json`（允许附带参数），或 JSON 对象缺少 `status`、`title` 中任一字段。 |
| `internal-detail` | 响应包含内部包名、异常类名、SQL 语句或调用栈条目。这是启发式检查，可能误报。 |
| `slow` | 收到完整响应的耗时超过 `--slow-seconds`，默认 2.0 秒。 |
| `valid-rejected` | 符合格式契约的语法生成文件未得到 200，或响应中的整数 `rows` 与预期班次数不一致。空行不计入班次。 |

`random` 生成随机字节或可打印字符行。`mutation` 从六个小文件及一个运行时生成的大文件出发，随机执行一至四次字节、词元、行或字段修改。词元字典包含标点、控制字节、编码边界以及日期和时间边界值。大文件包含 5,000–20,000 行，由固定种子确定，不写入源码目录。

`grammar.json` 及其有界展开逻辑改编自[语法生成实验](https://github.com/codexary/payslate-spike-grammar-fuzzer)。每个文件内的分隔符、换行符和可选列保持一致；根据 [ADR 0012](../docs/adr/0012-timesheet-upload-limits.zh-CN.md)，未知附加列会被忽略，因此语法重新包含可选的 `notes` / `break minutes` 列，独立的格式契约检查也接受这些列，但仍拒绝重复的已知列。姓名仅使用初始化时检查或创建的合成员工。语法覆盖表头别名、日期、时间、跨夜班次、BOM、空行、引号内的分隔符及双引号转义；格式契约拒绝引号内换行，因此语法不生成此类输入，由变异策略覆盖。各策略使用独立的随机数流。在 Python 3.12 下，使用相同种子和相同工具文件可以复现候选输入的字节；耗时和服务状态仍可能使结果不同。

事件目标会创建一个覆盖 2026 年 7 月 6–12 日的工资批次，然后发送随机事件名，或对 API 规范中的 `calculate`、`approve`、`pay`、`reverse` 做变异。该目标的 `all` 只包含 random 和 mutation，不支持 grammar。每个事件都编码为一个 URL 路径段。所有请求共用一个批次，因此后续状态转换的响应可能受前面请求和缩减尝试的影响。

`--target employees` 向 `POST /v1/employees` 发送 JSON 请求体。随机策略改变字段值及类型；变异策略从合法请求体出发，每次修改一个字段，覆盖姓名长度和控制字符、`1e20`、`-0`、`12.345`、超长数字串及错误类型。`all` 仅运行 random 和 mutation；grammar 和 `valid-rejected` 不适用，其余四项检查保持不变。此目标无需初始化员工，成功请求会创建员工，保存的 `input.bin` 即为 JSON 请求体。

## 本地运行

使用 Python 3.12，无需安装第三方 Python 包。Java、Docker 和开发身份发行者的准备步骤见[根目录说明](../README.zh-CN.md#payslate-online-api)。在仓库根目录执行以下命令；Compose 构建镜像前，须先构建 API jar：

```sh
export PAYSLATE_DB_USER=payslate PAYSLATE_DB_PASSWORD=local-dev-db
export PAYSLATE_DEV_ISSUER_ADMIN=admin PAYSLATE_DEV_ISSUER_PASSWORD=local-dev-admin
export PAYSLATE_API_PORT=18080 PAYSLATE_ISSUER_PORT=18081 PAYSLATE_GRAFANA_PORT=13000
export COMPOSE_PROJECT_NAME=payslate-robustness
./mvnw -B -ntp package
docker compose up --build -d
```

等待 `http://localhost:18081/realms/payslate/.well-known/openid-configuration` 和 `http://localhost:18080/actuator/health/readiness` 成功响应，然后按根目录说明使用 direct grant 获取合成操作员账户的 token：

```sh
TOKEN=$(curl --fail -s http://localhost:18081/realms/payslate/protocol/openid-connect/token \
  -d grant_type=password -d client_id=payslate-ui \
  -d username=officer-a -d password=dev-only-a \
  | python3 -c 'import json, sys; print(json.load(sys.stdin)["access_token"])')
python3 -m unittest discover -s robustness
python3 robustness/campaign.py --base-url http://localhost:18080 --token "$TOKEN" --setup --budget 0
python3 robustness/campaign.py --base-url http://localhost:18080 --token "$TOKEN" \
  --strategy all --budget 300 --seed 20260716
python3 robustness/campaign.py --base-url http://localhost:18080 --token "$TOKEN" \
  --target pay-run-events --strategy all --budget 100 --seed 20260716
```

长时间运行前或 token 过期后，可用同一条命令重新获取。认证失败会终止测试，不会计作合法文件被拒绝。`--setup` 也可以与正式测试一起使用：它在 token 所属雇主下先精确查找姓名，再创建缺失员工；已有且唯一的员工会直接复用，出现重名则停止。请勿同时执行多个初始化过程。

预算只计算原始候选输入，在所选策略间轮流分配。每个请求的超时为 120 秒。网络错误和超时单独记录，并使程序以状态码 2 退出，不伪造 HTTP 响应；仅发现问题不会改变正常退出码 0。每 50 个候选输入打印一行进度，发现新类别并开始缩减时也会提示。最后一行为 JSON 汇总。初始化和缩减请求不占候选输入预算。

检查完成后，删除临时服务栈及其数据：

```sh
docker compose down -v
```

## 查看结果

已知问题：协议层面无效的 URI（例如含编码后的 NUL，即 `%00`）会在进入应用前被 Servlet 容器拒绝，返回 400 和 Tomcat 的最简 HTML 页面，而非 problem details，因此事件目标会报告 `problem-format`；该页面不含服务器版本，也不回显 URI。

`robustness/out/` 已被 Git 忽略，多次运行之间会保留。`findings.json` 包含各次运行的汇总和每个问题类别的代表输入。类别定义为 `(判据名称, 状态码, 响应 title, detail 前 60 个字符并将数字替换为 #)`，目录名由该元组的哈希值生成。不同原因若产生相同的公开响应，可能归入同一类别。候选数量和各判据计数只统计原始响应，不包括缩减尝试。某个策略在预算内可能找不到任何问题。

每个类别目录包含 `input.bin`、不含 token 的请求说明 `request.txt`，以及记录状态码、耗时、响应头和响应体的 `response.txt`。上传目标的 `input.bin` 是 multipart 中的文件内容；事件目标的文件则是编码到路径段中的原始字节。记录的工资批次 ID 属于当时的临时数据库，在新服务栈中重放时需重新创建批次，并自行提供有效的 bearer token。

缩减先删除整行片段，再删除字节片段，只保留仍能触发同一类别的输入。`--minimise-requests` 默认每个新类别最多尝试 40 次，包含首次确认重放；被本地检查拒绝的尝试也占用额度。合法文件的缩减结果必须仍通过独立的格式契约检查，并重新计算预期行数。这种有限次数的缩减不保证得到最小输入。`reproduced: false` 表示首次确认未能重现，因而保留原始输入和响应。延迟问题可能受预热和负载影响。后续运行发现相同类别时，会保留先前保存的代表文件。

每次运行的 JSONL 文件记录候选输入的策略、大小、哈希值、状态码、耗时、解析后的响应和触发的判据，也会保留普通的 400 响应。例如，Windows-1252 文件不属于 UTF-8 语法的有效输入；即使接口错误地将其解释为姓名不匹配，也不一定触发这五项检查。工具并不保证所有非法输入都得到最恰当的错误说明，小预算内没有发现问题也不代表没有缺陷。所有输入均为合成数据。
