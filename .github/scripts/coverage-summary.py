#!/usr/bin/env python3
"""
Kover's XML report (JaCoCo's format) as a Markdown table for the run
summary: line and branch coverage overall, then the ten least-covered
packages with at least 20 lines.

    python3 .github/scripts/coverage-summary.py app/build/reports/kover/reportFdroidDebug.xml Fdroid
"""
import sys

from defusedxml import ElementTree

MEASURES = {"LINE": "Lines", "BRANCH": "Branches"}
LEAST_COVERED = 10
MIN_LINES = 20


def counters(element) -> dict[str, tuple[int, int]]:
    # Each <counter type=… missed=… covered=…> directly under the element.
    return {
        c.get("type"): (int(c.get("covered")), int(c.get("missed")))
        for c in element.findall("counter")
    }


def percent(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 * covered / total if total else 100.0


def main(report: str, flavor: str) -> None:
    root = ElementTree.parse(report).getroot()
    total = counters(root)
    print(f"## Unit test coverage ({flavor} debug)\n")
    print("| | Covered | Missed | Coverage |")
    print("|---|---|---|---|")
    for measure, label in MEASURES.items():
        covered, missed = total.get(measure, (0, 0))
        print(f"| {label} | {covered} | {missed} | {percent(covered, missed):.1f}% |")
    packages = []
    for package in root.findall("package"):
        covered, missed = counters(package).get("LINE", (0, 0))
        if covered + missed >= MIN_LINES:
            packages.append((percent(covered, missed), package.get("name").replace("/", "."), covered + missed))
    if packages:
        print(f"\n**Least covered packages** (at least {MIN_LINES} lines)\n")
        print("| Package | Lines | Line coverage |")
        print("|---|---|---|")
        for share, name, lines in sorted(packages)[:LEAST_COVERED]:
            print(f"| `{name}` | {lines} | {share:.1f}% |")


if __name__ == "__main__":
    main(*sys.argv[1:3])
