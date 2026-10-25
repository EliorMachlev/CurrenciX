#!/usr/bin/env python3
"""
Adds to gradle/verification-metadata.xml the other genuine copies of a file
the build has downloaded.

Some files are published with different bytes in different repositories:
a plugin's marker POM on Maven Central isn't the one on the Gradle Plugin
Portal, though both point to the same plugin. Which copy a build gets
depends on which repository answers first (Central throttles at times, and
Gradle moves on to the next), so a metadata file that knows only one copy
fails on the other. Gradle records just the copy it resolved, so this adds
each other copy that its cache holds for the same file as an <also-trust>
checksum. Only files Gradle itself downloaded from the build's own
repositories are in that cache, so nothing outside them gets trusted.

    python3 .github/scripts/trust-mirror-copies.py ~/.gradle gradle/verification-metadata.xml
"""
import hashlib
import pathlib
import re
import sys

COMPONENT = re.compile(r'<component group="(?P<group>[^"]+)" name="(?P<name>[^"]+)" version="(?P<version>[^"]+)"')
ARTIFACT = re.compile(r'<artifact name="(?P<file>[^"]+)"')
SHA256 = re.compile(r'^(?P<indent>\s*)<sha256 value="(?P<value>[0-9a-f]{64})"(?P<rest>[^/>]*)(?P<close>/?)>\s*$')
ALSO_TRUST = re.compile(r'<also-trust value="(?P<value>[0-9a-f]{64})"')
INDENT = "   "


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cached_copies(files: pathlib.Path, group: str, name: str, version: str, file: str) -> set[str]:
    # files-2.1/<group>/<name>/<version>/<sha1 of the copy>/<file>
    return {sha256(copy) for copy in (files / group / name / version).glob(f"*/{file}") if copy.is_file()}


def main(gradle_home: str, metadata: str) -> None:
    files = pathlib.Path(gradle_home) / "caches" / "modules-2" / "files-2.1"
    path = pathlib.Path(metadata)
    lines = path.read_text().splitlines()
    out: list[str] = []
    component = artifact = None
    added = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        if match := COMPONENT.search(line):
            component = match.group("group", "name", "version")
        elif match := ARTIFACT.search(line):
            artifact = match.group("file")
        elif (match := SHA256.match(line)) and component and artifact:
            known = {match.group("value")}
            block = [line]
            if not match.group("close"):
                # Already has <also-trust> lines: keep them and their closing tag.
                i += 1
                while "</sha256>" not in lines[i]:
                    known.update(m.group("value") for m in ALSO_TRUST.finditer(lines[i]))
                    block.append(lines[i])
                    i += 1
                block.append(lines[i])
            extra = sorted(cached_copies(files, *component, artifact) - known)
            if extra:
                indent, value, rest = match.group("indent", "value", "rest")
                kept = block[1:-1] if len(block) > 1 else []
                block = (
                    [f'{indent}<sha256 value="{value}"{rest}>']
                    + kept
                    + [f'{indent}{INDENT}<also-trust value="{v}"/>' for v in extra]
                    + [f"{indent}</sha256>"]
                )
                added += len(extra)
            out.extend(block)
            i += 1
            continue
        out.append(line)
        i += 1
    path.write_text("\n".join(out) + "\n")
    print(f"Trusted {added} other genuine copies.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
