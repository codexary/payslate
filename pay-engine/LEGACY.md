English | [简体中文](LEGACY.zh-CN.md)

# Inherited Payslate 7 engine

The calculation routine in `LegacyPayEngine` was written in 2004. In 2021 the desktop team moved it to a current Java build with minimal changes, introducing records for its data types and `java.time` for dates. The remaining calculation code deliberately retains 2004 style: one long calculation routine, mutable counters, manual loops, an anonymous comparator, mutable collections, `double` intermediate pay, and sequential schedule scanning. The surrounding API, CLI and tests are new code. The source is excluded from Spotless until the planned refactor. These choices are recorded here so they are not a style example.
