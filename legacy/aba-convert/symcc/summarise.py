#!/usr/bin/env python3
# Read the repeated hybrid/afl-pair runs under a compare directory and summarise:
#   - edges_found at fixed time points, median and range across runs, per config;
#   - accepted batches whose amounts differ from the seed's yet still reconcile,
#     with the time the first one appeared, per config.
# It reads each run's out/main/plot_data and replays every queue input under a
# plain build of the converter. No result is hardcoded.
#   Usage: python3 symcc/summarise.py [compare-dir]   (default symcc/out/compare)
import sys, os, subprocess, statistics, re, glob

BASE = sys.argv[1] if len(sys.argv) > 1 else "symcc/out/compare"
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # aba-convert dir
os.chdir(HERE)
POINTS = [60, 120, 180, 240, 300, 360, 420, 480, 540, 600]

subprocess.run(["make"], check=True, stdout=subprocess.DEVNULL)
PLAIN = "./aba-convert"

def seed_amount_sets():
    sets = set()
    for f in glob.glob("fuzz/seeds/*.txt"):
        amts = []
        for ln in open(f, "rb").read().split(b"\n"):
            if ln[:1] == b"D" and len(ln) >= 67:
                amts.append(ln[57:67].decode("latin1"))
        sets.add(",".join(amts))
    return sets
SEEDS = seed_amount_sets()

def plot_rows(path):
    rows = []
    if not os.path.exists(path):
        return rows
    for ln in open(path):
        ln = ln.strip()
        if not ln or ln.startswith("#"):
            continue
        c = ln.split(",")
        try:
            rows.append((int(c[0]), int(c[12])))  # relative_time, edges_found
        except (IndexError, ValueError):
            pass
    return rows

def edges_at(rows, t):
    v = 0
    for rt, e in rows:
        if rt <= t:
            v = e
        else:
            break
    return v

def run_start(rundir, cfg):
    """Approximate run start: the earliest mtime among the seed queue entries both
    instances write at startup. Container and host share one clock, so mtimes are
    comparable."""
    ms = []
    for qd in glob.glob(os.path.join(rundir, cfg, "out", "*", "queue")):
        for f in glob.glob(os.path.join(qd, "id:*")):
            try:
                ms.append(os.path.getmtime(f))
            except OSError:
                pass
    return min(ms) if ms else None


def replay_new(qdir, t0):
    """Count accepted queue inputs whose amounts differ from every seed, and the
    earliest one's time in whole seconds from the run start. AFL names carry
    `time:` in ms; the SymCC helper's names do not, so time is taken from the
    file mtime relative to t0 (approximate, in seconds)."""
    cnt, earliest = 0, None
    for q in sorted(glob.glob(os.path.join(qdir, "id:*"))):
        if os.path.isdir(q):
            continue
        try:
            r = subprocess.run([PLAIN, q], capture_output=True, timeout=10)
        except subprocess.TimeoutExpired:
            continue
        if r.returncode != 0:
            continue
        amts = [ol[-10:].decode("latin1") for ol in r.stdout.split(b"\n")
                if ol[:1] == b"1" and len(ol) >= 10]
        key = ",".join(amts)
        if key and key not in SEEDS:
            cnt += 1
            if t0 is not None:
                try:
                    t = int(round(os.path.getmtime(q) - t0))
                except OSError:
                    t = None
                if t is not None:
                    earliest = t if earliest is None else min(earliest, t)
    return cnt, earliest

def collect(cfg):
    per_point = {p: [] for p in POINTS}
    news, firsts = [], []
    for rd in sorted(glob.glob(os.path.join(BASE, "run-*"))):
        rows = plot_rows(os.path.join(rd, cfg, "out", "main", "plot_data"))
        for p in POINTS:
            per_point[p].append(edges_at(rows, p))
        t0 = run_start(rd, cfg)
        ctot, efirst = 0, None
        for qdir in glob.glob(os.path.join(rd, cfg, "out", "*", "queue")):
            c, e = replay_new(qdir, t0)
            ctot += c
            if e is not None:
                efirst = e if efirst is None else min(efirst, e)
        news.append(ctot)
        firsts.append(efirst)
    return per_point, news, firsts

print("compare dir:", BASE)
print("seed amount sets:", sorted(SEEDS))
for cfg in ("hybrid", "afl-pair"):
    pp, news, firsts = collect(cfg)
    print(f"[{cfg}]")
    for p in POINTS:
        vs = [v for v in pp[p] if v is not None]
        if vs:
            print(f"  t={p:>4}s  edges median={statistics.median(vs):>4}  range=[{min(vs)}..{max(vs)}]  n={len(vs)}")
    print(f"  accepted batches with non-seed amounts, per run: {news}")
    ft = [f for f in firsts if f is not None]
    tail = f"  earliest={min(ft)}s" if ft else "  (none)"
    print(f"  first non-seed reconciling batch (s), per run: {firsts}{tail}")
