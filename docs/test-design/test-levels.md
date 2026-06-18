English | [简体中文](test-levels.zh-CN.md)

# Test levels and integration order

| Level | Test | Failure caught here |
| --- | --- | --- |
| Unit | `UnitTimesheetCsvTest`, pay-run and engine tests | CSV partitions, state and calculation branches without infrastructure |
| API | `ApiOpenApiContractTest` | Documented paths differing from the running Spring mappings |
| Integration | `IntegrationPostgresMigrationTest` (`docker`) | Flyway or JDBC behavior against PostgreSQL |
| System | `SystemWholePayrollFlowTest` | CSV upload through calculation, approval, payment, ABA download and parse |
| Acceptance | `AcceptanceMargaretPayrollTest` | Margaret's rule that payment requires approval and reversal requires payment |

Assembly order: calculator and importer first, then JDBC with a real PostgreSQL container, then MVC and whole-flow tests. The system test uses H2 as a database stand-in so `verify` runs without Docker; the Docker integration test checks the database seam. The Pact spike supplies a stub rostering provider for the consumer contract. The live third-party provider is not available in this repository.

The PostgreSQL integration test uses JWT principals with two employer IDs and checks that one cannot read the other’s employee, pay run, or payslip. The system test checks public-holiday pay, pay-period filtering, ABA cents and totals; the OpenAPI test checks request and response schemas.
