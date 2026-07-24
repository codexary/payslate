English | [简体中文](README.zh-CN.md)

# Payslate

Payslate began as payroll software for small and medium businesses. Payslate Online is the hosted service being built around the inherited 2004 pay engine. The current modules are `pay-engine` and `bank-file`; a web API and database will add timesheet import, pay runs and ABA downloads. This repository uses synthetic data only. Bank test BSBs check field shape; no generated file is intended for bank submission.

## Build and run

Java 21 is required. Run `./mvnw -B -ntp verify`. The `docker` profile is reserved for container integration tests. To run the demonstration CLI:

```sh
./mvnw -q -pl pay-engine package
java -cp pay-engine/target/classes com.elsworne.payslate.engine.PayCli pay-engine/demo-timesheet.csv 20 PERMANENT
```

Expected output:

```text
Gross: 20.00
Tax: 0.00
Net: 20.00
```

The tax schedule in `pay-engine/src/main/resources/example-tax-schedule.csv` is an **example for tests**, not official tax guidance. Production must load official tables each financial year. The CLI CSV is deliberately small; the rostering format spike explores the product importer.

Coverage: `./mvnw -B -ntp verify` writes `pay-engine/target/site/jacoco/index.html` and `jacoco.xml`; the pay-engine branch gate is 65%. See `docs/test-design/overtime-control-flow.md`.

Mutation analysis: `./mvnw -B -ntp -pl pay-engine test-compile org.pitest:pitest-maven:mutationCoverage` writes HTML and XML under `pay-engine/target/pit-reports/`. See `docs/quality/mutation-report.md` for the measured baseline.

Seeded calendar and pay-path campaign: `./mvnw -B -ntp -pl pay-engine -am -Dtest=PayCalendarRandomTest -Dsurefire.failIfNoSpecifiedTests=false test`. Distribution, elapsed-time oracle, CSV overlap limits and measured results: [random calendars](docs/test-design/random-calendars.md).

Seeded HTTP robustness testing: the manual [API campaign](robustness/) sends synthetic timesheets and pay-run events through a local Compose stack, compares random, mutation and grammar inputs, and saves reduced finding cases. It is not part of CI.

## Payslate Online API

Build with `./mvnw -B -ntp verify`. For a local PostgreSQL run, set `PAYSLATE_DB_USER`, `PAYSLATE_DB_PASSWORD`, `PAYSLATE_DEV_ISSUER_ADMIN`, and `PAYSLATE_DEV_ISSUER_PASSWORD` to local development values, run `./mvnw -pl payslate-api -am package`, then `docker compose up --build`. The API listens on host port 8080; set `PAYSLATE_API_PORT` to use another port. `payslate-api/openapi.yaml` documents employees, employee search, CSV timesheets, pay runs, payslips and ABA downloads. The liveness and readiness URLs are `/actuator/health/liveness` and `/actuator/health/readiness`. Errors use `application/problem+json`; logs use structured ECS JSON. `./mvnw -B -ntp -Pdocker verify` also runs PostgreSQL Testcontainers tests. CSV uploads accept comma, semicolon and tab delimiters, the header aliases from ADR 0003, ISO and Australian dates, 24-hour and 12-hour times and quoted names. Uploads must be UTF-8 (optional BOM), at most 1 MB and at most 10 000 data rows, with years 2000–2099. Quoted line breaks and duplicated known columns are rejected; unknown extra columns are ignored. A header or row error names its line; a file that is not UTF-8 is rejected as a whole. It rejects mixed columns, invalid dates and empty shifts. All data in the tests is synthetic, and the bundled tax schedule is an example only; production must load official tables each financial year.

Metrics export is disabled by default. Set `PAYSLATE_METRICS_EXPORT=true` to enable OTLP export and `PAYSLATE_OTLP_METRICS_URL` to select the collector endpoint (default `http://localhost:4318/v1/metrics`). `PAYSLATE_METRICS_STEP` sets the export interval (default `1m`). Compose enables export to `http://observability:4318/v1/metrics` every `5s`; these variables can override its defaults. `PAYSLATE_GRAFANA_PORT` changes Grafana’s host port (default `3000`). The service name is `payslate-api`; HTTP timer tags contain only the route template, method and status.

The local Compose stack includes a development OIDC issuer at `http://localhost:8081/realms/payslate`; `PAYSLATE_ISSUER_PORT` changes its host port. Its synthetic staff accounts are `officer-a` (employer `synthetic-a`), `officer-b` (employer `synthetic-b`), and `employee-a`; their fixture passwords are in `payslate-api/dev-issuer/payslate-realm.json` and are for local testing only. Sign in through the OIDC authorization-code flow, or use the development realm's direct grant to obtain a bearer token for API experiments. A bearer token must carry `employer_id` and a `PAYROLL_OFFICER` or `EMPLOYEE` realm role. Officers manage their own employer's employees and pay runs; an employee may read only their own payslip. Officers can search their own employees by name at `GET /v1/employees/search?q=<text>`; the search text is a bound query parameter and the results are scoped to the caller's employer. All database lookups include the employer ID.

For any request naming an employee, pay run or payslip by ID, `404 Not Found` means the record is not visible to the caller, whether or not it exists.

The issuer starts more slowly than the API: wait until `http://localhost:8081/realms/payslate/.well-known/openid-configuration` answers before asking for a token. A token for `officer-a` from the development issuer (change the port if you set `PAYSLATE_ISSUER_PORT`); the `access_token` field of the response is the bearer token:

```sh
curl -s http://localhost:8081/realms/payslate/protocol/openid-connect/token \
  -d grant_type=password -d client_id=payslate-ui -d username=officer-a -d password=dev-only-a
```

Create a pay run with JSON `{"periodStart":"2026-02-23","periodEnd":"2026-03-01"}`. Only shifts wholly inside the inclusive dates are calculated. CSV may include a `public holiday` column with `true`/`false`, `yes`/`no`, or `1`/`0`; missing means false. An optional standing `deductions` amount on employee creation defaults to zero. If deductions exceed gross minus tax, calculation rejects the entire run with an error naming the employee.

Create a calendar pay run at `POST /v1/pay-runs/calendar` with `{"frequency":"WEEKLY","anchorPayDate":"2026-03-01","payDate":"2026-03-01"}`. The API stores the inclusive period 2026-02-23 through 2026-03-01; 2026-03-02 is not on that calendar and is rejected. Frequencies are `WEEKLY`, `FORTNIGHTLY` and `MONTHLY`; monthly periods are calendar months paid on their last day.

The service constructs its pay engine using `payslate.time-zone`, defaulting to `Australia/Melbourne`; set `PAYSLATE_TIME_ZONE` or override the Spring property to select another zone. Payroll pays elapsed time in this single configured zone, while date, weekday and week rules use local time. Ambiguous local times use the earlier offset, and skipped local times move forward through the gap. CSV cannot distinguish the later occurrence of a repeated hour. Per-employer zones remain an open decision.

## Monitoring

With the Compose stack running, open Grafana at `http://localhost:3000` (or the port set by `PAYSLATE_GRAFANA_PORT`) and select **Payslate → Payslate API**. The dashboard is provisioned automatically and refreshes every 5 seconds; allow a few exports after sending API requests. Panels sample at 10-second intervals and show per-interval peaks for maximum latency and saturation, so a single slow request is visible.

Request rate shows which routes are busy. The 4xx panel groups rejected requests by route and status; the 5xx panel shows server failures by route and reads zero when there are none. The latency panel shows p95 and maximum request duration in seconds by route. The two saturation panels show busy Tomcat request threads and active Hikari database connections as percentages of their configured capacities (200 threads and 10 connections), using the peak per 10-second interval. Each legend shows the maximum and last percentage, helping the person on call judge how much capacity is in use. HTTP panels exclude `/actuator.*` health probes; saturation gauges cover the whole service.

The **Denied requests by route and status** panel shows the peak rate per interval for 401, 403 and record-not-found 404 responses, using only `route` and `status` counter tags. Each denial also writes an ECS JSON event with `event.action=access-denied`, `event.outcome=failure`, `http.response.status_code`, `http.request.method`, `http.route`, and verified JWT claims `sub` and `employer_id` when present; tokens, bodies, query strings and raw path variables are excluded, and unmatched routes use `UNKNOWN`. Denial counters for every `/v1` route template (and `UNKNOWN`) start at zero so a single denial is visible, and the panel lists only series that have counted a denial; 5xx timers cannot be pre-registered, so a first 500 on a route can show as zero rate until the next one.

## About this repository

Elsworne, Payslate and the team are fictional.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
