/* Payslate desktop ABA batch converter, carried over from the desktop product. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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
  int total = 0;
  int count = 0;
  while (fgets(line, sizeof line, in) != NULL) {
    if (line[0] != 'D' || strlen(line) < 67) {
      fprintf(stderr, "invalid detail\n");
      fclose(in);
      return 2;
    }
    char source_name[41];
    char account_name[24];
    char bsb[8];
    char account[10];
    char amount[11];
    field(source_name, line, 1, 40);
    strcpy(account_name, source_name);
    field(bsb, line, 41, 7);
    field(account, line, 48, 9);
    field(amount, line, 57, 10);
    int cents = atoi(amount);
    total += cents;
    count++;
    printf("1%-7s%-9s%-24s%010d\n", bsb, account, account_name, cents);
  }
  printf("7%010d%06d\n", total, count);
  fclose(in);
  return 0;
}
