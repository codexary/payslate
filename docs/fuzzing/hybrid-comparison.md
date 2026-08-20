English | [简体中文](hybrid-comparison.zh-CN.md)

# Hybrid fuzzing vs AFL++ alone on the converter

Two configurations on the `aba-convert` reconciliation gate, on the same machine,
the same two CPUs each, the same seeds and the same budget:

- **hybrid** — one AFL++ main instance with `symcc_fuzzing_helper` as a secondary,
  so SymCC solves branch conditions on the main instance's queue and offers the
  solved inputs back;
- **afl-pair** — one AFL++ main instance with a second AFL++ instance and no
  SymCC, so both configurations spend the same two CPUs.

## Setup

- Image `payslate-aba-symcc`: SymCC `3b8acabf06c8` with the QSYM backend and
  AFL++ `v5.02c`, both built from LLVM 15; built and run arm64-native, with no
  emulation (unlike the KLEE image).
- Each configuration pinned to two disjoint cores with `--cpuset-cpus` (hybrid on
  0,1; afl-pair on 2,3), run at the same time so neither steals the other's CPU.
- Seeds `fuzz/seeds/one-record.txt` and `fuzz/seeds/two-records.txt` (both
  reconcile) and the dictionary `fuzz/aba.dict`.
- Five runs of 10 minutes per configuration, with distinct AFL++ seeds 1–5.
- Read from each run's `plot_data` and queues by `symcc/summarise.py`; the raw
  runs are not committed (regenerate with `symcc/compare.sh 5 600`).

## Edge coverage over time

`edges_found` from the main instance's `plot_data`, median and range across the
five runs:

| time | hybrid (median [min..max]) | afl-pair (median [min..max]) |
| ---: | --- | --- |
| 120 s | 106 [106..107] | 107 [105..107] |
| 300 s | 107 [107..107] | 107 [106..107] |
| 600 s | 107 [107..107] | 107 [107..107] |

Both reach 107 edges within the first two or three minutes and stay there; there
is no edge-coverage difference. The reconciled seeds already drive the converter
through to `write_batch`, so the edges past the reconciliation gate are covered
from the start, and crossing the gate with different amounts would add none.

## Batches that reconcile with amounts different from the seed

Every queued input replayed under a plain build, counting those accepted with
amounts not in the seed corpus:

| | run 1 | run 2 | run 3 | run 4 | run 5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| hybrid | 0 | 0 | 0 | 0 | 0 |
| afl-pair | 0 | 0 | 0 | 0 | 0 |

Neither configuration produced a reconciling batch with new amounts in the ten
minutes. To solve the gate toward *new* amounts the helper needs a queue entry
that fails the gate (a mutated-amount batch); the comparison did not record
which inputs the helper received.

## Reading

On this converter, at this budget, running SymCC beside AFL++ bought nothing over
AFL++ alone: the same edge coverage and no new reconciling batch. SymCC does cross
the reconciliation gate when it is pointed straight at a mutated-amount batch —
`symcc/run-symcc.sh symcc/seeds/mismatch.txt` solves one, and that solved batch is
kept as a regression and as a seed — but as a continuous loop beside the fuzzer it
did not pay here. ADR 0017 records where that leaves hybrid fuzzing.
