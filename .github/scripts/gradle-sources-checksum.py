#!/usr/bin/env python3
"""
Records in gradle/verification-metadata.xml the checksum of Gradle's own
source zip (gradle-<version>-src.zip) for the wrapper's version. An IDE's
Gradle sync (Android Studio, Qodana's project import) downloads it to show
Gradle's API in build scripts, and the build itself never does, so
recording the build's checksums doesn't add it. The checksum is the one
Gradle publishes next to the file (services.gradle.org). An entry for an
older Gradle is replaced.

    python3 .github/scripts/gradle-sources-checksum.py gradle/wrapper/gradle-wrapper.properties gradle/verification-metadata.xml
"""
import pathlib
import re
import sys
import urllib.request

DISTRIBUTIONS = "https://services.gradle.org/distributions"
VERSION = re.compile(r"distributionUrl=.*/gradle-(?P<version>[^-/]+)-(?:bin|all)\.zip")
ENTRY = re.compile(r'\n      <component group="gradle" name="gradle" version="[^"]+">.*?</component>', re.DOTALL)
COMPONENTS = "   <components>\n"
ORIGIN = "Gradle's published checksum (services.gradle.org)"


def wrapper_version(properties: pathlib.Path) -> str:
    match = VERSION.search(properties.read_text())
    if not match:
        sys.exit(f"{properties}: no distributionUrl with a version")
    return match["version"]


def published_sha256(version: str) -> str:
    with urllib.request.urlopen(f"{DISTRIBUTIONS}/gradle-{version}-src.zip.sha256", timeout=60) as response:
        value = response.read().decode().strip()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        sys.exit(f"Not a SHA-256 for gradle-{version}-src.zip: {value!r}")
    return value


def entry(version: str, sha256: str) -> str:
    return (
        f'\n      <component group="gradle" name="gradle" version="{version}">\n'
        f'         <artifact name="gradle-{version}-src.zip">\n'
        f'            <sha256 value="{sha256}" origin="{ORIGIN}"/>\n'
        f"         </artifact>\n"
        f"      </component>"
    )


def main(properties: str, metadata: str) -> None:
    version = wrapper_version(pathlib.Path(properties))
    path = pathlib.Path(metadata)
    text = ENTRY.sub("", path.read_text())
    # Components are kept in group order; "gradle" goes before the first group after it.
    groups = list(re.finditer(r'\n      <component group="([^"]+)"', text))
    after = next((m for m in groups if m.group(1) > "gradle"), None)
    at = after.start() if after else text.index("\n   </components>")
    text = text[:at] + entry(version, published_sha256(version)) + text[at:]
    if COMPONENTS not in text:
        sys.exit(f"{metadata}: no <components> block")
    path.write_text(text)
    print(f"Recorded gradle-{version}-src.zip")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
