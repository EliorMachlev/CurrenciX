#!/usr/bin/env python3
"""
Prints the frame-timing benchmark numbers from Macrobenchmark's
*benchmarkData.json files as a Markdown table, for the run summary and the
job log, so a run's numbers can be compared without downloading artifacts.

    python3 .github/scripts/benchmark-summary.py baselineprofile/build/outputs

Writes the table to $GITHUB_STEP_SUMMARY when set, and one notice
annotation per benchmark. Missing or empty results print a note instead of
failing: the benchmark step is advisory.
"""
import os
import pathlib
import sys

from benchmark_results import PERCENTILES, annotation, rows


def cell(value) -> str:
    return "–" if value is None else f"{value:.1f}"


def main() -> None:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    found = list(rows(root))
    if not found:
        table = "No frame-timing results (the benchmark step failed or the emulator reported no frame stats)."
    else:
        header = "| Benchmark | Metric | " + " | ".join(PERCENTILES) + " |\n|---|---|" + "---|" * len(PERCENTILES)
        lines = [f"| {n} | {m} | " + " | ".join(cell(v) for v in vs) + " |" for n, m, vs in found]
        table = header + "\n" + "\n".join(lines)
        for n, m, vs in found:
            print(annotation("notice", f"Benchmark {n}", f"{m} " + ", ".join(f"{p} {cell(v)}" for p, v in zip(PERCENTILES, vs))))
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("## Frame-timing benchmarks (emulator, advisory)\n\n" + table + "\n")


if __name__ == "__main__":
    main()
