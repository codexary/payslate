English | [简体中文](CONTRIBUTING.zh-CN.md)

# Contributing

- Use Java 21 and run `./mvnw -B -ntp verify` before asking for review. The build checks Java formatting with Spotless (google-java-format); `./mvnw spotless:apply` fixes it. `LegacyPayEngine` is excluded from formatting until it is refactored.
- Work on a branch named `<type>/<topic>`, for example `fix/midnight-day-rate`, and open a pull request with the template. Commit messages follow Conventional Commits.
- A defect fix starts with a test that fails on the current code. The test and the fix go in the same pull request.
- Expected pay comes from the written rule confirmed by the product owner, never from what the engine currently returns.
- Use synthetic data only. Never commit real employee names, tax file numbers or bank details.
