"""Prove properties of the withholding schedule with Z3, reading the schedule from
the CSV the pay engines use rather than hard-coding the brackets.

The engines compute base + (gross - minimum) * rate and round the total to cents
with HALF_UP, reading minimum, base and rate from example-tax-schedule.csv. This
script reads the same CSV (and checks the pay-engine and legacy-engine copies are
identical), builds the piecewise model in integer cents with exact rational
arithmetic, and proves, for every whole-cent gross from $0.00 to $1,000,000.00:

  * withholding is never negative and never exceeds gross;
  * it is continuous where each pair of brackets joins;
  * raising gross by one cent changes withholding by 0 or 1 cent, so net pay never
    falls (this needs every rate to be between 0 and 1, which the script checks).

For each sloped bracket it derives the step period from the rate (rate p/q adds p
cents every q cents of gross) and verifies it, and has Z3 report the smallest gross
at which the bracket first steps. Finally it shows that a deliberately discontinuous
schedule makes the continuity proof fail -- so the check is checking the CSV, not a
copy of it baked into the code. The run is deterministic.
"""
import sys
from decimal import Decimal
from fractions import Fraction

from z3 import And, If, Implies, Int, IntVal, Not, Optimize, Solver, sat, unsat

CENTS_MAX = 100_000_000  # $1,000,000.00
CSVS = [
    "pay-engine/src/main/resources/example-tax-schedule.csv",
    "legacy-engine/src/main/resources/example-tax-schedule.csv",
]


def read_schedule(path):
    rows = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            minimum, base, rate = line.split(",")
            rows.append((int(Decimal(minimum) * 100), int(Decimal(base) * 100), Fraction(Decimal(rate))))
    rows.sort(key=lambda r: r[0])
    return rows


def halfup(x, rate):
    """HALF_UP(x * rate) for integer cents x >= 0, as a Z3 Int. For p/q and x >= 0,
    (2*x*p + q) // (2*q) rounds half away from zero, matching BigDecimal HALF_UP."""
    if isinstance(x, int):
        x = IntVal(x)
    p, q = rate.numerator, rate.denominator
    return (2 * x * p + q) / (2 * q)


def withholding(g, brackets):
    minimum, base, rate = brackets[-1]
    expr = base + halfup(g - minimum, rate)
    for i in range(len(brackets) - 2, -1, -1):
        minimum, base, rate = brackets[i]
        upper = brackets[i + 1][0]
        expr = If(g < upper, base + halfup(g - minimum, rate), expr)
    return expr


def proves(claim):
    s = Solver()
    s.add(Not(claim))
    return s.check() == unsat


def prove(name, claim):
    ok = proves(claim)
    print("  %-7s %s" % ("proved" if ok else "FAILED", name))
    if not ok:
        raise SystemExit("property does not hold: " + name)


def main():
    schedules = [read_schedule(p) for p in CSVS]
    if any(s != schedules[0] for s in schedules):
        raise SystemExit("the pay-engine and legacy-engine schedules differ")
    brackets = schedules[0]
    print("schedule read from CSV (%d brackets); pay-engine and legacy-engine copies identical" % len(brackets))
    for minimum, base, rate in brackets:
        if not (0 <= rate <= 1):
            raise SystemExit("rate %s at minimum %d is outside 0..1" % (rate, minimum))

    g = Int("g")
    in_range = And(g >= 0, g <= CENTS_MAX)
    has_next = And(g >= 0, g < CENTS_MAX)
    w = lambda e: withholding(e, brackets)

    print("properties (every whole-cent gross from $0.00 to $1,000,000.00):")
    prove("withholding is non-negative", Implies(in_range, w(g) >= 0))
    prove("withholding does not exceed gross", Implies(in_range, w(g) <= g))
    for i in range(1, len(brackets)):
        lo_min, lo_base, lo_rate = brackets[i - 1]
        hi_min, hi_base, _ = brackets[i]
        prove("continuous where the bracket at $%d.%02d begins" % (hi_min // 100, hi_min % 100),
              lo_base + halfup(hi_min - lo_min, lo_rate) == hi_base)
    prove("a one-cent rise changes withholding by 0 or 1 cent",
          Implies(has_next, And(w(g + 1) - w(g) >= 0, w(g + 1) - w(g) <= 1)))
    prove("net pay never falls when gross rises one cent",
          Implies(has_next, (g + 1 - w(g + 1)) >= (g - w(g))))

    print("step period (derived from each rate, then verified):")
    for i, (minimum, base, rate) in enumerate(brackets):
        if rate == 0:
            continue
        upper = brackets[i + 1][0] if i + 1 < len(brackets) else CENTS_MAX
        q, p = rate.denominator, rate.numerator
        prove("rate %s: +%d cent(s) every %d cents of gross" % (rate, p, q),
              Implies(And(g >= minimum, g + q <= upper), w(g + q) - w(g) == p))

    print("representative step points (Z3 gives the smallest in each sloped bracket):")
    for i, (minimum, base, rate) in enumerate(brackets):
        if rate == 0:
            continue
        upper = brackets[i + 1][0] if i + 1 < len(brackets) else CENTS_MAX
        opt = Optimize()
        gg = Int("g")
        opt.add(gg > minimum, gg < upper, withholding(gg, brackets) - withholding(gg - 1, brackets) == 1)
        opt.minimize(gg)
        assert opt.check() == sat
        c = opt.model()[gg].as_long()
        print("  rate %-5s first steps at gross $%d.%02d" % (rate, c // 100, c % 100))

    # Counterexample: a discontinuous schedule must make the continuity proof fail,
    # which shows the check follows the CSV rather than a copy baked into the code.
    print("counterexample (a deliberately discontinuous schedule):")
    bad = list(brackets)
    bi = len(bad) - 1  # the top bracket (its base $50 -> $60 breaks continuity at $1000)
    bmin, bbase, brate = bad[bi]
    bad[bi] = (bmin, bbase + 1000, brate)  # shift this bracket's base up by $10.00
    lo_min, lo_base, lo_rate = bad[bi - 1]
    continuous = lo_base + halfup(bmin - lo_min, lo_rate) == bad[bi][1]
    if proves(continuous):
        raise SystemExit("the discontinuous schedule was wrongly accepted")
    print("  FAILED (as expected)  continuity at $%d.%02d once its base is shifted by $10.00"
          % (bmin // 100, bmin % 100))


if __name__ == "__main__":
    main()
