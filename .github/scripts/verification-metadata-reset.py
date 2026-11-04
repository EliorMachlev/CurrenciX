#!/usr/bin/env python3
"""
Empties the <components> of gradle/verification-metadata.xml, keeping its
<configuration> (what is verified, and the files trusted without a checksum,
exceptions.md #9). Recording again from there lists only the files the build
fetches now: a version nothing asks for any more doesn't linger, still
counted as a dependency by the scanners that read the file.

    python3 .github/scripts/verification-metadata-reset.py gradle/verification-metadata.xml
"""
import pathlib
import re
import sys

COMPONENTS = re.compile(r"<components>.*</components>", re.DOTALL)
EMPTY = "<components>\n   </components>"


def main(path: str) -> None:
    file = pathlib.Path(path)
    text, count = COMPONENTS.subn(EMPTY, file.read_text())
    if count != 1:
        sys.exit(f"{path}: expected one <components> block, found {count}")
    file.write_text(text)
    print(f"Emptied the components of {path}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
