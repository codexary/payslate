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

## Payslate Online API

Build with `./mvnw -B -ntp verify`. For a local PostgreSQL run, set `PAYSLATE_DB_USER` and `PAYSLATE_DB_PASSWORD` to non-default values, run `./mvnw -pl payslate-api -am package`, then `docker compose up --build`. The API listens on host port 8080; set `PAYSLATE_API_PORT` to use another port. `payslate-api/openapi.yaml` documents employees, CSV timesheets, pay runs, payslips and ABA downloads. The liveness and readiness URLs are `/actuator/health/liveness` and `/actuator/health/readiness`. Errors use `application/problem+json`; logs use structured ECS JSON. `./mvnw -B -ntp -Pdocker verify` also runs PostgreSQL Testcontainers tests. The CSV accepts comma, semicolon and tab delimiters, the header aliases from ADR 0003, ISO and Australian dates, 24-hour and 12-hour times, UTF-8 BOM and quoted names. It rejects mixed columns, invalid dates and empty shifts. All data in the tests is synthetic, and the bundled tax schedule is an example only; production must load official tables each financial year.

## About this repository

Elsworne, Payslate and the team are fictional.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
