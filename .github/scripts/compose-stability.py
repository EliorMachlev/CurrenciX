#!/usr/bin/env python3
"""
Checks the Compose compiler's report (build with -PcomposeReports) for
composables that would recompose more than they need to:

- one that's restartable but not skippable, so it runs again whenever its
  parent does;
- a parameter whose type the compiler can't prove stable. With strong
  skipping such a parameter is compared by identity, so a fresh but equal
  value recomposes every time. Make the type stable (immutable, @Stable or
  @Immutable when that's true, or listed in app/compose-stability.conf).

    python3 .github/scripts/compose-stability.py app/build/compose-reports

Not flagged, being compared by identity on purpose: keys typed Any (a
restart or reset key), an Android Context, and an extension's receiver.
Nor are the home-screen widget's (Glance) composables: they compose once
per widget update into a static view, so skipping never comes into it.
Each finding is an error annotation, and the run fails.
"""
import pathlib
import re
import sys

FUNCTION = re.compile(r"^(?P<flags>(?:\w+ )*)(?:scheme\((?P<scheme>\S+)\) )?fun (?P<name>[\w.<>]+)\(")
GLANCE = "androidx.glance.GlanceComposable"
UNSTABLE = re.compile(r"^\s+unstable (?P<param>[^:]+): (?P<type>[^=]+?)(?: = .*)?$")
BY_IDENTITY = {"Any", "Any?", "Context", "Context?"}


def findings(report: str):
    current = None
    for line in report.splitlines():
        match = FUNCTION.match(line)
        if match:
            flags = match["flags"].split()
            # Only a restartable composable skips: helpers that always run
            # with their caller (remember…, inline ones) have nothing to skip.
            restartable = "restartable" in flags and GLANCE not in (match["scheme"] or "")
            current = match["name"] if restartable else None
            if restartable and "skippable" not in flags:
                yield current, "restartable but not skippable"
            continue
        unstable = UNSTABLE.match(line)
        if unstable and current:
            param, kind = unstable["param"].strip(), unstable["type"].strip()
            if param != "<this>" and kind not in BY_IDENTITY:
                yield current, f"unstable parameter {param}: {kind}"


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    reports = sorted(root.glob("*-composables.txt"))
    if not reports:
        print(f"::error title=Compose stability::No composables report in {root} (build with -PcomposeReports).")
        return 1
    found = [hit for path in reports for hit in findings(path.read_text())]
    for name, problem in found:
        print(f"::error title=Compose stability: {name.rsplit('.', 1)[-1]}::{name}: {problem}")
    if not found:
        print("Compose stability: every composable is skippable, and its parameters stable.")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
