English | [简体中文](README.zh-CN.md)

# Seeded HTTP robustness campaign

This manual campaign sends synthetic inputs to the team's local Payslate API. It observes HTTP responses without changing product code. It is not part of CI. Use a disposable local stack: accepted uploads persist shifts, and event requests can change a pay run's state.

The whole Compose stack matters: multipart handling, authentication, CSV parsing, employee lookup, database writes and error mapping all contribute to the response. A parser-only test cannot observe these interactions. Metrics and the Grafana dashboard can help explain latency while the campaign runs.

## Checks and generation

A response may trigger several checks:

| Report name | Finding condition |
| --- | --- |
| `server-error` | HTTP status is at least 500. |
| `problem-format` | A 4xx/5xx response lacks `application/problem+json` (parameters allowed), or its JSON object lacks `status` or `title`. |
| `internal-detail` | The body exposes package identifiers, exception names, SQL statements or a stack frame. This heuristic can produce false positives. |
| `slow` | Complete response takes more than `--slow-seconds` (default 2.0 seconds). |
| `valid-rejected` | A contract-valid grammar file does not receive 200 with the expected integer `rows` count. Blank rows do not count. |

`random` generates bytes or printable lines. `mutation` makes one to four byte, token, line or field edits to the six small seeds and a runtime seed of 5,000–20,000 rows. The token dictionary includes punctuation, control bytes, encoding boundaries and date/time boundary values. Large seeds are generated deterministically and are not stored in the source tree.

`grammar.json` and its bounded expansion adapt the [grammar fuzzer spike](https://github.com/codexary/payslate-spike-grammar-fuzzer). Delimiters, newlines and optional columns stay consistent within each file. Unknown extra columns are ignored under [ADR 0012](../docs/adr/0012-timesheet-upload-limits.md), so the grammar includes optional `notes` / `break minutes` columns and the independent contract check accepts them; duplicated known columns remain invalid. Names refer only to the synthetic employees checked or created by setup. The grammar covers aliases, dates, times, overnight shifts, BOM, blank rows, quoted delimiters and doubled quotes; the contract rejects quoted line breaks, so the grammar excludes them and mutation covers them. Each strategy has an independent seeded random stream. Identical seeds reproduce candidate bytes with Python 3.12 and the same campaign files; timings and server state can still change outcomes.

The event target creates one pay run for 6–12 July 2026 and sends random names or mutations of `calculate`, `approve`, `pay` and `reverse`, as listed in the API specification. `all` uses only random and mutation for this target; grammar is unavailable. Every event is encoded as a single URL path segment. The pay run is shared, so later transition responses may depend on earlier requests and reduction attempts.

`--target employees` posts JSON bodies to `POST /v1/employees`. Random generation varies fields and types; mutation changes one field of a valid body, including name lengths and control characters, `1e20`, `-0`, `12.345`, long digit strings and wrong types. `all` runs random and mutation only; grammar and `valid-rejected` do not apply. The other four checks are unchanged. No employee setup is needed, accepted requests create employees, and saved `input.bin` files contain the JSON body.

## Run locally

Use Python 3.12; no third-party Python packages are needed. See the [root setup instructions](../README.md#payslate-online-api) for Java, Docker and the development issuer. From the repository root, build the API jar before Compose builds its image:

```sh
export PAYSLATE_DB_USER=payslate PAYSLATE_DB_PASSWORD=local-dev-db
export PAYSLATE_DEV_ISSUER_ADMIN=admin PAYSLATE_DEV_ISSUER_PASSWORD=local-dev-admin
export PAYSLATE_API_PORT=18080 PAYSLATE_ISSUER_PORT=18081 PAYSLATE_GRAFANA_PORT=13000
export COMPOSE_PROJECT_NAME=payslate-robustness
./mvnw -B -ntp package
docker compose up --build -d
```

Wait for `http://localhost:18081/realms/payslate/.well-known/openid-configuration` and `http://localhost:18080/actuator/health/readiness` to respond successfully. Obtain the synthetic officer's token using the root README's direct grant:

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

Refresh the token with the same command before a long run or after expiry. Authentication failures stop the campaign instead of being counted as valid-file failures. `--setup` can also accompany a campaign. It searches for exact names before creating employees for the token's employer; existing unique matches are reused, while duplicate names cause a stop. Setup runs should not overlap.

The budget counts original candidates only, divided round-robin among strategies. Each request has a 120-second timeout. Network failures and timeouts are logged separately and cause exit code 2; they are not fabricated HTTP responses. Findings alone leave exit code 0. A progress line appears every 50 candidates, with additional notices when a new class is reduced. The last line is the JSON summary. Setup and reduction requests are outside the candidate budget.

After inspection, remove the disposable stack and its data:

```sh
docker compose down -v
```

## Read the results

Known finding: the event target reports `problem-format` for protocol-invalid URIs such as an encoded NUL (`%00`), which the servlet container rejects before the application runs with a 400 Tomcat minimal HTML page rather than problem details; the page contains no server version and does not echo the URI.

`robustness/out/` is ignored by Git and retained between runs. `findings.json` contains summaries and a representative for each distinct class. A class is `(oracle, status, response title, first 60 detail characters with digits replaced by #)`; its directory name is a hash of that tuple. Separate causes with identical public responses can collapse into one class. Candidate counts and oracle totals cover original responses only; they exclude reduction attempts. A strategy may find nothing within its budget.

Each class directory holds `input.bin`, a token-free `request.txt` describing the POST and payload encoding, and `response.txt` containing status, elapsed time, headers and body. For uploads, `input.bin` is the file part; for events, it contains the bytes encoded into the path segment. Recorded pay-run IDs belong to the disposable database; create a new run when replaying against a new stack. Supply your own current bearer token when replaying.

Reduction first removes line chunks, then byte chunks, retaining the same class. `--minimise-requests` defaults to 40 per new class, including a confirmation replay; locally rejected attempts also consume the cap. Valid-file reductions must still pass an independent contract check, and their expected row counts are recomputed. Results are bounded reductions, not guaranteed smallest inputs. `reproduced: false` means the confirmation failed and the original input and response were retained. Latency findings may be sensitive to warmup and load. Earlier saved representatives are preserved if another run finds the same class.

The per-run JSONL files record each candidate's strategy, size, hash, status, duration, parsed response and triggered checks. They also retain ordinary 400 responses: for example, Windows-1252 input is outside the UTF-8 grammar, and a misleading employee-name error does not necessarily trigger any of these five checks. The campaign does not assert that every invalid input receives the best explanation, nor does a small budget establish absence of faults. Inputs contain synthetic data only.
