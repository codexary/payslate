#include <klee/klee.h>
#include <assert.h>

/* The ten-character amount field, with ten symbolic digits. read_batch keeps the
   field to digits and parses it with strtoll; the value is the same ten-digit
   number computed here. KLEE has no path on which it exceeds the ten-digit
   ceiling, so read_batch's `cents > 9999999999` branch is unreachable. */
int main(void) {
  unsigned char d[10];
  klee_make_symbolic(d, sizeof d, "amount");
  for (int i = 0; i < 10; i++)
    if (d[i] < '0' || d[i] > '9') return 2;  /* read_batch rejects non-digits */
  long long c = 0;
  for (int i = 0; i < 10; i++) c = c * 10 + (d[i] - '0');
  if (c > 9999999999LL) klee_assert(0);       /* claimed unreachable */
  return 0;
}
