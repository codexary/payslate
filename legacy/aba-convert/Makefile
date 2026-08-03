CC ?= cc
CFLAGS ?= -std=c17 -Wall -Wextra -Wpedantic -O1 -g

all: aba-convert

aba-convert: aba-convert.c
	$(CC) $(CFLAGS) -o $@ $<

san: CFLAGS += -fsanitize=address,undefined -fno-omit-frame-pointer
san: clean aba-convert

afl: CC = afl-cc
afl: CFLAGS = -std=c17 -Wall -Wextra -O1 -g -fsanitize=address,undefined -fno-sanitize-recover=undefined -fno-omit-frame-pointer
afl: clean aba-convert

clean:
	rm -f aba-convert

.PHONY: all san afl clean
