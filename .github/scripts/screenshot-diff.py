"""Compares this branch's Roborazzi screenshots with alpha's.

Usage: screenshot-diff.py <alpha dir> <branch dir> <out dir> <summary file>

Each PNG is matched by file name. A screenshot whose bytes differ from
alpha's is changed (Roborazzi writes the same bytes for the same pixels);
one alpha doesn't have is new, and one only alpha has is gone. Changed
and new screenshots are copied to <out dir> (a changed one as
<name>.alpha.png beside <name>.png) to be uploaded, and the counts and
names are added to <summary file> (the run summary) as Markdown, with a
warning annotation when anything changed. Always exits 0: a change is for
a person to look at, not a failure.
"""

import filecmp
import shutil
import sys
from pathlib import Path


def pngs(root: Path) -> dict[str, Path]:
    found: dict[str, Path] = {}
    for path in sorted(root.rglob("*.png")):
        if path.name in found:
            sys.exit(f"Two screenshots are named {path.name}: {found[path.name]} and {path}")
        found[path.name] = path
    return found


def section(title: str, names: list[str]) -> list[str]:
    if not names:
        return []
    return [f"### {title} ({len(names)})", "", *(f"- `{name}`" for name in names), ""]


def main() -> None:
    alpha_dir, branch_dir, out_dir, summary = (Path(arg) for arg in sys.argv[1:5])
    alpha, branch = pngs(alpha_dir), pngs(branch_dir)
    changed = [name for name in branch if name in alpha and not filecmp.cmp(branch[name], alpha[name], shallow=False)]
    added = [name for name in branch if name not in alpha]
    removed = [name for name in alpha if name not in branch]

    out_dir.mkdir(parents=True, exist_ok=True)
    for name in changed:
        shutil.copyfile(branch[name], out_dir / name)
        shutil.copyfile(alpha[name], out_dir / f"{Path(name).stem}.alpha.png")
    for name in added:
        shutil.copyfile(branch[name], out_dir / name)

    unchanged = len(branch) - len(changed) - len(added)
    lines = [
        "## Screenshots against alpha",
        "",
        f"{len(changed)} changed, {len(added)} new, {len(removed)} gone, {unchanged} unchanged.",
        "",
        *section("Changed", changed),
        *section("New", added),
        *section("Gone", removed),
    ]
    if changed or added:
        lines.append("The changed and new screenshots (with alpha's beside each changed one) are in this run's `screenshot-changes-<commit>` artifact.")
    with summary.open("a", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    if changed or added or removed:
        print(f"::warning title=Screenshots::{len(changed)} changed, {len(added)} new, {len(removed)} gone against alpha")


if __name__ == "__main__":
    main()
