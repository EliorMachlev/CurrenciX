#!/usr/bin/env python3
"""
Records in gradle/verification-metadata.xml the checksum of Gradle's own
source zip (gradle-<version>-src.zip) for the wrapper's version. An IDE's
Gradle sync (Android Studio, Qodana's project import) downloads it to show
Gradle's API in build scripts, and the build itself never does, so
recording the build's checksums doesn't add it. The checksum is the one
Gradle publishes next to the file (services.gradle.org), fetched by the
caller (this script makes no requests). An entry for an older Gradle is
replaced.

    PROPS=gradle/wrapper/gradle-wrapper.properties
    curl -fsSL --proto =https "$(python3 .github/scripts/gradle-sources-checksum.py url "$PROPS")" -o src.zip.sha256
    python3 .github/scripts/gradle-sources-checksum.py record "$PROPS" gradle/verification-metadata.xml src.zip.sha256
"""
import pathlib
import re
import sys

DISTRIBUTIONS = "https://services.gradle.org/distributions"
VERSION = re.compile(r"distributionUrl=.*/gradle-(?P<version>[0-9][0-9A-Za-z.]*)-(?:bin|all)\.zip")
ENTRY = re.compile(r'\n      <component group="gradle" name="gradle" version="[^"]+">.*?</component>', re.DOTALL)
COMPONENTS = "   <components>\n"
ORIGIN = "Gradle's published checksum (services.gradle.org)"


def wrapper_version(properties: pathlib.Path) -> str:
    match = VERSION.search(properties.read_text())
    if not match:
        sys.exit(f"{properties}: no distributionUrl with a version")
    return match["version"]


def checksum_url(version: str) -> str:
    return f"{DISTRIBUTIONS}/gradle-{version}-src.zip.sha256"


def read_sha256(path: pathlib.Path, version: str) -> str:
    value = path.read_text().strip()
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


def record(properties: str, metadata: str, checksum_file: str) -> None:
    version = wrapper_version(pathlib.Path(properties))
    sha256 = read_sha256(pathlib.Path(checksum_file), version)
    path = pathlib.Path(metadata)
    text = ENTRY.sub("", path.read_text())
    if COMPONENTS not in text:
        sys.exit(f"{metadata}: no <components> block")
    # Components are kept in group order; "gradle" goes before the first group after it.
    after = next((m for m in re.finditer(r'\n      <component group="([^"]+)"', text) if m.group(1) > "gradle"), None)
    at = after.start() if after else text.index("\n   </components>")
    path.write_text(text[:at] + entry(version, sha256) + text[at:])
    print(f"Recorded gradle-{version}-src.zip")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "url":
        print(checksum_url(wrapper_version(pathlib.Path(sys.argv[2]))))
    elif len(sys.argv) == 5 and sys.argv[1] == "record":
        record(*sys.argv[2:])
    else:
        sys.exit(__doc__)
