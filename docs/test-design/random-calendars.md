English | [简体中文](random-calendars.zh-CN.md)

# Seeded random pay calendars

## Trial and oracle

`PayCalendarRandomTest` prints seed `202010310027` and makes 20,000 reproducible draws. Each trial draws a year uniformly from 2019–2030, a frequency with equal weekly, fortnightly and monthly weights, and an anchor uniformly from 1–14 January 2020. It draws a date uniformly within the selected year, then with probability one eighth replaces it with the first Sunday of April or October, with equal probability. The remaining seven eighths can also land on a transition day. Local start hours are uniform from 00–23; a skipped start hour moves forward under `atZone`. The end is the zoned start plus a uniformly drawn 1–12 elapsed hours. This deliberately weighted distribution is for finding faults; customer operational profiles were not available.

Every trial checks that pay dates belong to the requested year and their half-open periods, that weekly and fortnightly dates have the required cadence, and that monthly calendars contain 12 periods. Named tests separately check 27 fortnightly pay dates for the 1 January 2020 anchor and that February 2020 contains 29 February but excludes 1 March.

The pay oracle measures minutes between the generated instants. The test passes only the local endpoints to the real `PayEngine`, as CSV ingestion would, with permanent employment, $20/hour, no public holiday and zero deductions. It compares the sum of ordinary and overtime hours with elapsed hours. Seed, zero-based trial number, frequency and both zoned endpoints identify a failed comparison.

Before the fix, trial 223 generated a fortnightly case from `2023-04-02T01:00+11:00[Australia/Melbourne]` to `2023-04-02T09:00+10:00[Australia/Melbourne]`. The engine paid 8.0000 hours against 9.0000 elapsed hours. The real pay path now resolves local endpoints in its configured zone, counts elapsed minutes, and advances a zoned clock minute by minute while retaining local dates, weekdays and weeks for pay rules. Its default is Melbourne. The explicit 01:00–09:00 regressions pay 9.0000 hours and $270.00 on 2 April 2023, and 7.0000 hours and $210.00 on 1 October 2023, including the existing Sunday and overtime rules. The parallel run of the migration oracle never generated a transition-crossing shift: it starts at 08:00 on 22–28 February 2026 and lasts at most 600 minutes.

## CSV overlap policy and its limit

The exact draws expose a representational limit: 15 generated ends use the later offset of the repeated 02:00 hour. CSV local times cannot carry that offset. The specified `atZone` policy selects the earlier offset in overlaps and moves skipped local times forward through gaps. Thus it is impossible both to preserve the original instant expectation for every draw and to pass every draw using this CSV contract.

The campaign keeps all 20,000 draws and checks the 19,985 representable shifts against the original instant oracle. For the 15 others it explicitly checks the documented CSV resolution policy: eight produce shorter paid durations and seven collapse to zero duration and must be rejected by the 1–1440-minute validation. These are reported separately; they are not claimed as original-instant matches. The first such case is trial 526, from `2024-04-07T00:00+11:00` to `2024-04-07T02:00+10:00`: the original duration is three hours, but the CSV end resolves to `02:00+11:00`, giving two. Preserving the later occurrence would require an input contract that carries an offset or instant.

## Measured results

The corrected campaign passes all calendar assertions and the pay assertions under the above distinction. On the same draws the test calls the unchanged `legacy-engine`, compares its paid hours with the original instant oracle, and counts rejected positive-elapsed shifts as disagreements. It does not substitute a reimplementation of the legacy calculation.

| Group | Legacy disagreements / trials | Relative frequency |
| --- | ---: | ---: |
| All | 276 / 20,000 | 1.380% |
| Weekly | 93 / 6,590 | 1.411% |
| Fortnightly | 90 / 6,757 | 1.332% |
| Monthly | 93 / 6,653 | 1.398% |
| Starts on transition day | 259 / 2,560 | 10.117% |
| Starts on another day | 17 / 17,440 | 0.097% |

Of the 276 disagreements, 269 return incorrect hours and seven reject equal local endpoints. All 276 cross an offset change, including the 17 that start the previous day. The 15 unrepresentable cases are included in these raw-oracle legacy totals; the remaining 261 disagreements are representable by CSV and corrected by the elapsed-time fix. The frequency groups have similar observed proportions, but frequency does not control shift generation or the duration calculation. These are sample relative frequencies under this generator, not production failure probabilities.

```text
calendar seed=202010310027 trials=20000
current raw-instant matches=19985/20000 csv-policy cases=15 (paid=8 rejected=7)
legacy raw-oracle disagreements=276/20000 (paid-hours=269 rejected=7)
WEEKLY legacy disagreements=93/6590
FORTNIGHTLY legacy disagreements=90/6757
MONTHLY legacy disagreements=93/6653
transition-day legacy disagreements=259/2560 other-day legacy disagreements=17/17440
offset crossings=276/20000 legacy disagreement crossings=276/276
simulation seed=202010310028 p=0.0100 confidence=0.95 n=299 formula=0.950464 detected=9530/10000 observed=0.9530
```

## Planning trial counts

For independent trials from a stable distribution with a stated per-trial probability `p` of an oracle-visible failure, `P(at least one detection) = 1 - (1-p)^n`. A target probability `c` requires `n = ceil(log(1-c) / log(1-p))`. At the stipulated `p = 0.01` and `c = 0.95`, the minimum is 299 trials and the calculated detection probability is 0.950464.

A separate Bernoulli simulation uses seed `202010310028` and runs 10,000 campaigns of 299 trials. It detects at least one event in 9,530 campaigns, an observed 0.9530, checked within 0.015 of the formula. It never calls the pay engine. This tests the simulation against the stipulated model, not the rate of payroll defects. A fixed seed enables replay; repeated runs of it are not independent replications. Neither 299 trials nor 20,000 passing checks imply a probability that the software is correct or a release guarantee.

## Operational interpretation

Customer pay-frequency weights, zones, shift start times, lengths and offset-crossing proportions are needed before estimating customer exposure. Repeated shifts within an employer or calendar may be correlated, so the unit of an independent trial must be defined before applying the planning formula. The deliberately weighted transition dates cannot establish a production failure rate. The hours oracle does not validate taxes, award rules, deductions or bank files; those need their own tests and review.

Run from the repository root:

```sh
./mvnw -B -ntp -pl pay-engine -am -Dtest=PayCalendarRandomTest -Dsurefire.failIfNoSpecifiedTests=false test
```
