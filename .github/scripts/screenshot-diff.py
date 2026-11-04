"""Compares this branch's Roborazzi screenshots with master's.

Usage: screenshot-diff.py <master dir> <branch dir> <out dir> <summary file>

Each PNG is matched by file name. A screenshot whose bytes differ from
master's is changed (Roborazzi writes the same bytes for the same pixels);
one master doesn't have is new, and one only master has is gone. Changed
and new screenshots are copied to <out dir> (a changed one as
<name>.master.png beside <name>.png) to be uploaded, and the counts and
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
    master_dir, branch_dir, out_dir, summary = (Path(arg) for arg in sys.argv[1:5])
    master, branch = pngs(master_dir), pngs(branch_dir)
    changed = [name for name in branch if name in master and not filecmp.cmp(branch[name], master[name], shallow=False)]
    added = [name for name in branch if name not in master]
    removed = [name for name in master if name not in branch]

    out_dir.mkdir(parents=True, exist_ok=True)
    for name in changed:
        shutil.copyfile(branch[name], out_dir / name)
        shutil.copyfile(master[name], out_dir / f"{Path(name).stem}.master.png")
    for name in added:
        shutil.copyfile(branch[name], out_dir / name)

    unchanged = len(branch) - len(changed) - len(added)
    lines = [
        "## Screenshots against master",
        "",
        f"{len(changed)} changed, {len(added)} new, {len(removed)} gone, {unchanged} unchanged.",
        "",
        *section("Changed", changed),
        *section("New", added),
        *section("Gone", removed),
    ]
    if changed or added:
        lines.append("The changed and new screenshots (with master's beside each changed one) are in this run's `screenshot-changes-<commit>` artifact.")
    with summary.open("a", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    if changed or added or removed:
        print(f"::warning title=Screenshots::{len(changed)} changed, {len(added)} new, {len(removed)} gone against master")


if __name__ == "__main__":
    main()
