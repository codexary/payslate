#include "../convert.h"
#include <klee/klee.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

/* Build a batch whose structure is concrete and whose amounts and control trailer
   are symbolic only in their low digits (the high digits are fixed to '0' so the
   values stay small and the paths bounded). KLEE then reasons about read_batch's
   reconciliation: for which amounts and trailer does the batch reconcile? That is
   the exact arithmetic gate — the declared total must equal the sum of the amounts
   — that random fuzzing cannot cross. read_batch's own digit checks decide the
   symbolic bytes, so no assumptions are needed. */
#ifndef NREC
#define NREC 1
#endif

int main(void) {
  unsigned char buf[11 + NREC * 68 + 18];
  klee_make_symbolic(buf, sizeof buf, "buf");
  size_t p = 0;
  memcpy(buf, "H,PAYSLATE\n", 11);
  p = 11;
  for (int r = 0; r < NREC; r++) {
    buf[p] = 'D';
    memset(buf + p + 1, 'A', 40);
    memcpy(buf + p + 41, "123-456", 7);
    memcpy(buf + p + 48, "000000001", 9);
    memset(buf + p + 57, '0', 8);        /* amount high 8 digits fixed to 0 */
    /* buf[p+65 .. p+66] (amount low 2 digits) stays symbolic: amount in 0..99 */
    buf[p + 67] = '\n';
    p += 68;
  }
  buf[p] = 'T';
  memset(buf + p + 1, '0', 5);           /* declared count high 5 digits fixed */
  /* buf[p+6] (count low digit) symbolic: 0..9 */
  memset(buf + p + 7, '0', 8);           /* declared total high 8 digits fixed */
  /* buf[p+15 .. p+16] (total low 2 digits) symbolic: 0..99 */
  buf[p + 17] = '\n';
  p += 18;

#ifdef DUMP_BATCH
  /* Replay/dump build only: KTEST_FILE made klee_make_symbolic read back the
     solved bytes above, so buf now holds the exact batch KLEE explored. Write it
     out (to $BATCH_OUT, else stdout) and stop before parsing, so klee/replay.sh
     can turn each .ktest into a tests/reconcile-cases input with no field layout
     duplicated outside this harness. */
  {
    const char *path = getenv("BATCH_OUT");
    FILE *bf = path ? fopen(path, "wb") : stdout;
    if (bf) { fwrite(buf, 1, p, bf); if (path) fclose(bf); }
    return 0;
  }
#endif

  struct record recs[NREC];
  size_t n = 0;
  long long total = 0;
  int rc = read_batch(buf, p, recs, NREC, &n, &total);
  if (rc == 0) {
    long long sum = 0;
    for (size_t i = 0; i < n; i++) sum += recs[i].cents;
    klee_assert((int) n == NREC && sum == total);
  }
  return rc;
}
