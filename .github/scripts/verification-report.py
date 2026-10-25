#!/usr/bin/env python3
"""
Names the files Gradle's dependency verification refused, from the HTML
reports it writes (build/reports/dependency-verification/at-*/), as one
error annotation and in the run summary, so they can be read on the PR
without opening a long log.

    python3 .github/scripts/verification-report.py build/reports/dependency-verification
"""
import os
import pathlib
import re
import sys

# A refused file's link: …/files-2.1/<group>/<name>/<version>/<sha1>/<file>
LINK = re.compile(r'files-2\.1/(?P<group>[^/"]+)/(?P<name>[^/"]+)/(?P<version>[^/"]+)/[0-9a-f]+/(?P<file>[^/"]+)"')
IDE_DOCUMENTATION = re.compile(r"-(sources|javadoc)\.jar$")


def refused(reports: pathlib.Path) -> list[str]:
    found = {
        f"{m['file']} ({m['group']}:{m['name']}:{m['version']})"
        for report in reports.glob("at-*/dependency-verification-report.html")
        for m in LINK.finditer(report.read_text(errors="replace"))
    }
    # Files the build itself uses first; IDE-only documentation after.
    return sorted(found, key=lambda line: (bool(IDE_DOCUMENTATION.search(line.split(" ")[0])), line))


def main(reports_dir: str) -> None:
    files = refused(pathlib.Path(reports_dir))
    if not files:
        print("No dependency verification report.")
        return
    lines = [f"Dependency verification refused {len(files)} files:"] + files
    # One annotation (a step keeps only the first few), newlines escaped.
    print("::error title=Dependency verification::" + "%0A".join(lines))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write("### Dependency verification refused\n\n" + "\n".join(f"- `{f}`" for f in files) + "\n")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
