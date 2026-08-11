/* Payslate desktop ABA batch converter, carried over from the desktop product. */
#include "convert.h"

#include <stdio.h>
#include <stdlib.h>

int main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "usage: aba-convert input.txt\n");
    return 2;
  }
  FILE *in = fopen(argv[1], "r");
  if (in == NULL) {
    fprintf(stderr, "usage: aba-convert input.txt\n");
    return 2;
  }
  /* Read the whole input once into a buffer, then validate and write from it.
     The batch is bounded to the record cap's worth of lines. */
  size_t cap = 1 << 16, len = 0;
  unsigned char *buf = malloc(cap);
  if (buf == NULL) {
    fprintf(stderr, "out of memory\n");
    fclose(in);
    return 2;
  }
  for (;;) {
    if (len == cap) {
      size_t ncap = cap * 2;
      if (ncap > (size_t) MAX_RECORDS * 68 + 1024) {
        fprintf(stderr, "input too large\n");
        free(buf);
        fclose(in);
        return 2;
      }
      unsigned char *nb = realloc(buf, ncap);
      if (nb == NULL) {
        fprintf(stderr, "out of memory\n");
        free(buf);
        fclose(in);
        return 2;
      }
      buf = nb;
      cap = ncap;
    }
    size_t got = fread(buf + len, 1, cap - len, in);
    len += got;
    if (got == 0) break;
  }
  if (ferror(in)) {
    fprintf(stderr, "read error\n");
    free(buf);
    fclose(in);
    return 2;
  }
  fclose(in);

  size_t rmax = len / 67 + 2;
  if (rmax > MAX_RECORDS) rmax = MAX_RECORDS;
  struct record *recs = malloc(rmax * sizeof *recs);
  if (recs == NULL) {
    fprintf(stderr, "out of memory\n");
    free(buf);
    return 2;
  }
  size_t n = 0;
  long long total = 0;
  int rc = read_batch(buf, len, recs, rmax, &n, &total);
  if (rc == 0) write_batch(recs, n, total);
  free(recs);
  free(buf);
  return rc;
}
