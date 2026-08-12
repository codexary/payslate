# Withholding schedule checks

`tax_schedule_check.py` proves, with Z3, that whatever withholding schedule the
pay engines read behaves for every whole-cent gross from $0.00 to $1,000,000.00.
It reads the schedule from the same CSV the engines use -- and checks the
pay-engine and legacy-engine copies are identical -- then builds the engines'
computation in integer cents with exact rational arithmetic (`base + (gross -
minimum) * rate`, rounded to cents with HALF_UP) and proves, for every bracket in
the file: withholding is non-negative, never exceeds gross, is continuous where
the brackets join, and changes by at most one cent when gross rises one cent (so
net pay never falls; this needs every rate between 0 and 1, which the script
checks). It derives each sloped bracket's step period from its rate, verifies it,
and reports the smallest gross at which each bracket first steps.

Because it reads the CSV, a change to the schedule is actually re-checked. The run
ends by showing that a deliberately discontinuous schedule -- the top bracket's
base raised by $10.00 -- makes the continuity proof fail, so the check cannot pass
a broken schedule unnoticed.

## Running

Python 3.12 with the pinned solver, from the repository root (the CSV paths are
relative to it):

    python3 -m venv .venv && . .venv/bin/activate
    pip install -r analysis/requirements.txt
    python3 analysis/tax_schedule_check.py

Every property prints `proved`, and the counterexample prints `FAILED (as
expected)`; the output is deterministic. To reproduce the failure by hand, edit a
bracket's base in example-tax-schedule.csv so a boundary no longer lines up and
run the script: the continuity property for that boundary prints `FAILED`.
