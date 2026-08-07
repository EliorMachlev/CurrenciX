#!/usr/bin/env python3
"""One-shot import sorter for the CurrenciX rename.

ktlint requires imports in strict alphabetical order. The mass package
rename produced files where the new `com.eliormachlev.currencix.*`
imports sort differently vs. `com.squareup.*` etc. This walks the given
paths, extracts the contiguous import block from each Kotlin file, and
rewrites it in sorted order.

Delete this script once its one job is done — it's a rename-migration
tool, not a permanent utility (Spotless already enforces order on CI).
"""
import sys
import pathlib


TRAILING_PREFIXES = ("java.", "javax.", "kotlin.")


def _sort_key(imp: str) -> tuple[int, str]:
    body = imp[len("import ") :].lstrip()
    for idx, prefix in enumerate(TRAILING_PREFIXES, start=1):
        if body.startswith(prefix):
            return (idx, imp)
    return (0, imp)


def sort_imports_in_file(path: pathlib.Path) -> bool:
    lines = path.read_text().splitlines()
    start = end = None
    for i, line in enumerate(lines):
        if line.startswith("import "):
            if start is None:
                start = i
            end = i
    if start is None:
        return False
    block = lines[start : end + 1]
    imports = [ln for ln in block if ln.startswith("import ")]
    sorted_imports = sorted(imports, key=_sort_key)
    if block == sorted_imports:
        return False
    new_lines = lines[:start] + sorted_imports + lines[end + 1 :]
    path.write_text("\n".join(new_lines) + "\n")
    return True


def main(argv: list[str]) -> int:
    changed = 0
    for arg in argv:
        p = pathlib.Path(arg)
        if p.is_dir():
            files = p.rglob("*.kt")
        else:
            files = [p]
        for f in files:
            if sort_imports_in_file(f):
                changed += 1
                print(f"sorted: {f}")
    print(f"total files rewritten: {changed}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
