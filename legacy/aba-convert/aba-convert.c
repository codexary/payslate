/* Payslate desktop ABA batch converter, carried over from the desktop product. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

static void field(char *out, const char *line, int start, int width) {
  memcpy(out, line + start, width);
  out[width] = '\0';
  for (int i = width - 1; i >= 0 && out[i] == ' '; i--) out[i] = '\0';
}

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
  char line[256];
  if (fgets(line, sizeof line, in) == NULL || strncmp(line, "H,PAYSLATE", 10) != 0) {
    fprintf(stderr, "invalid header\n");
    fclose(in);
    return 2;
  }
  puts("0                 PAYSLATE SYNTHETIC BATCH");
  long long total = 0;
  int count = 0;
  while (fgets(line, sizeof line, in) != NULL) {
    if (line[0] != 'D' || strlen(line) < 67) {
      fprintf(stderr, "invalid detail\n");
      fclose(in);
      return 2;
    }
    char source_name[41];
    char account_name[25];
    char bsb[8];
    char account[10];
    char amount[11];
    field(source_name, line, 1, 40);
    /* Bound the copy to the 24-character output field (plus a NUL). */
    snprintf(account_name, sizeof account_name, "%.24s", source_name);
    field(bsb, line, 41, 7);
    field(account, line, 48, 9);
    field(amount, line, 57, 10);
    /* Accept digits only (no sign, no empty field), parse in 64 bits and keep the
       total within the ten-digit output fields. */
    if (amount[0] == '\0') {
      fprintf(stderr, "invalid amount\n");
      fclose(in);
      return 2;
    }
    for (int i = 0; amount[i] != '\0'; i++)
      if (amount[i] < '0' || amount[i] > '9') {
        fprintf(stderr, "invalid amount\n");
        fclose(in);
        return 2;
      }
    errno = 0;
    char *end;
    long long cents = strtoll(amount, &end, 10);
    if (errno != 0 || *end != '\0' || cents < 0 || cents > 9999999999LL) {
      fprintf(stderr, "amount out of range\n");
      fclose(in);
      return 2;
    }
    total += cents;
    if (total > 9999999999LL) {
      fprintf(stderr, "batch total out of range\n");
      fclose(in);
      return 2;
    }
    count++;
    printf("1%-7s%-9s%-24s%010lld\n", bsb, account, account_name, cents);
  }
  printf("7%010lld%06d\n", total, count);
  fclose(in);
  return 0;
}
