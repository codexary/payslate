package com.elsworne.payslate.api;

import com.code_intelligence.jazzer.junit.FuzzTest;

class TimesheetImporterFuzzTest {
  @FuzzTest(maxDuration = "2m")
  void importer(byte[] data) {
    try {
      TimesheetCsv.parse(new java.io.ByteArrayInputStream(data));
    } catch (IllegalArgumentException expected) {
      /* a malformed timesheet is a normal, handled rejection */
    }
  }
}
