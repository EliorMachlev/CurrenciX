#!/usr/bin/env python3
"""
Removes the CodeQL findings recorded in .github/codeql/exceptions.yml (and
explained in docs/markDown/engineering/exceptions.md) from the SARIF the analysis wrote,
before it's uploaded. A finding is removed only when its rule, file, line and
message all match one entry, so the rule keeps reporting everywhere else.
Fails when an entry matches nothing: the finding is gone, so its exception
should go too.

    python3 .github/scripts/codeql-exceptions.py .github/codeql/exceptions.yml sarif
"""
import json
import pathlib
import sys

RULE = "excluded-rule"
FIELDS = (RULE, "file", "line", "message-contains")


def read_exceptions(path: pathlib.Path) -> list[dict[str, str]]:
    # A list of flat "key: value" maps; each entry starts with "- ".
    entries: list[dict[str, str]] = []
    for raw in path.read_text().splitlines():
        line = raw.split("#", 1)[0].rstrip() if not raw.lstrip().startswith("#") else ""
        if not line.strip():
            continue
        if line.lstrip().startswith("- "):
            entries.append({})
            line = line.lstrip()[2:]
        key, _, value = line.strip().partition(":")
        entries[-1][key.strip()] = value.strip()
    for entry in entries:
        missing = [field for field in FIELDS if field not in entry]
        if missing:
            sys.exit(f"{path}: an entry is missing {', '.join(missing)}: {entry}")
    return entries


def location(result: dict) -> tuple[str, int]:
    physical = (result.get("locations") or [{}])[0].get("physicalLocation", {})
    uri = physical.get("artifactLocation", {}).get("uri", "")
    return uri, physical.get("region", {}).get("startLine", 1)


def matches(result: dict, entry: dict[str, str]) -> bool:
    uri, line = location(result)
    return (
        result.get("ruleId") == entry[RULE]
        and uri == entry["file"]
        and line == int(entry["line"])
        and entry["message-contains"] in result.get("message", {}).get("text", "")
    )


def main(exceptions_file: str, sarif_dir: str) -> None:
    entries = read_exceptions(pathlib.Path(exceptions_file))
    used = [0] * len(entries)
    for sarif in sorted(pathlib.Path(sarif_dir).glob("*.sarif")):
        log = json.loads(sarif.read_text())
        for run in log.get("runs", []):
            kept = []
            for result in run.get("results", []):
                hit = next((i for i, entry in enumerate(entries) if matches(result, entry)), None)
                if hit is None:
                    kept.append(result)
                else:
                    used[hit] += 1
                    uri, line = location(result)
                    print(f"Excluded (exceptions.md): {result['ruleId']} at {uri}:{line}")
            run["results"] = kept
        sarif.write_text(json.dumps(log))
    stale = [entry for entry, count in zip(entries, used) if count == 0]
    for entry in stale:
        print(f"::error file={exceptions_file}::{entry[RULE]} no longer reported at {entry['file']}:{entry['line']}; "
              "remove the exception (docs/markDown/engineering/exceptions.md)")
    if stale:
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
