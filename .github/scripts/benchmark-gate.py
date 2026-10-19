#!/usr/bin/env python3
"""
Fails the run when the app got slower: compares each benchmark's median
(P50) with the saved baseline, baselineprofile/benchmark-baseline.json.

    python3 .github/scripts/benchmark-gate.py baselineprofile/build/outputs baselineprofile/benchmark-baseline.json

A metric regresses when it's both TOLERANCE slower than the baseline and at
least MIN_DELTA_MS slower: CI runs on an emulator, whose numbers wander, so
small swings and fast metrics' noise don't fail it. Each regression is an
error annotation, and the run fails.

It can't judge, and passes with a notice, when the benchmarks gave no
numbers (the emulator sometimes reports no frame stats) or there's no
baseline yet. Either way it writes this run's medians to benchmark-current.json
(uploaded with the results): commit that file as the baseline to start
checking against it, or to accept a slowdown that's deliberate.
"""
import json
import pathlib
import sys

from benchmark_results import medians

TOLERANCE = 0.25
MIN_DELTA_MS = 5.0
CURRENT_FILE = "benchmark-current.json"


def regressions(current: dict, baseline: dict):
    for key, before in sorted(baseline.items()):
        now = current.get(key)
        if now is None or before is None:
            continue
        if now > before * (1 + TOLERANCE) and now - before >= MIN_DELTA_MS:
            yield key, before, now


def main() -> int:
    results = pathlib.Path(sys.argv[1])
    baseline_path = pathlib.Path(sys.argv[2])
    current = medians(results)
    pathlib.Path(CURRENT_FILE).write_text(json.dumps(current, indent=2, sort_keys=True) + "\n")
    if not current:
        print("::notice title=Benchmark check::No benchmark numbers this run, so nothing to compare.")
        return 0
    if not baseline_path.is_file():
        print(f"::notice title=Benchmark check::No baseline yet. Commit {CURRENT_FILE} from this run's artifact as {baseline_path} to start checking.")
        return 0
    found = list(regressions(current, json.loads(baseline_path.read_text())))
    for key, before, now in found:
        name, metric = key.split("|", 1)
        print(f"::error title=Slower: {name}::{metric} P50 {now:.1f} ms, baseline {before:.1f} ms (+{(now / before - 1) * 100:.0f}%)")
    if not found:
        print(f"Benchmark check: {len(current)} medians within {TOLERANCE:.0%} of the baseline.")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
